package com.minierp.sales.order;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minierp.common.error.BusinessException;
import com.minierp.common.error.ErrorCode;
import com.minierp.common.jpa.LikePatterns;
import com.minierp.common.web.CurrentUser;
import com.minierp.inventory.StockAllocation;
import com.minierp.inventory.StockLedger;
import com.minierp.master.item.Item;
import com.minierp.master.item.ItemRepository;
import com.minierp.master.partner.Partner;
import com.minierp.master.partner.PartnerRepository;
import com.minierp.master.partner.PartnerRole;
import com.minierp.master.warehouse.Warehouse;
import com.minierp.master.warehouse.WarehouseRepository;
import com.minierp.sales.order.SalesOrder.LineSpec;
import com.minierp.sales.order.SalesOrderDtos.ActionRequest;
import com.minierp.sales.order.SalesOrderDtos.CreateRequest;
import com.minierp.sales.order.SalesOrderDtos.LineRequest;
import com.minierp.sales.order.SalesOrderDtos.ReasonRequest;
import com.minierp.sales.order.SalesOrderDtos.Response;
import com.minierp.sales.order.SalesOrderDtos.Summary;
import com.minierp.sales.order.SalesOrderDtos.UpdateRequest;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

/**
 * 수주 업무 규칙과 트랜잭션 경계.
 *
 * 확정·취소·마감은 수주 상태와 재고 할당을 한 트랜잭션에서 함께 바꾼다. 가용 재고가 모자라 할당이 실패하면
 * 상태 전이와 이력도 함께 롤백되어 "확정됐는데 할당은 없는" 수주가 생기지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class SalesOrderService {

	private final SalesOrderRepository salesOrderRepository;
	private final PartnerRepository partnerRepository;
	private final WarehouseRepository warehouseRepository;
	private final ItemRepository itemRepository;
	private final StockLedger stockLedger;
	private final EntityManager entityManager;

	public SalesOrderService(SalesOrderRepository salesOrderRepository, PartnerRepository partnerRepository,
			WarehouseRepository warehouseRepository, ItemRepository itemRepository, StockLedger stockLedger,
			EntityManager entityManager) {
		this.salesOrderRepository = salesOrderRepository;
		this.partnerRepository = partnerRepository;
		this.warehouseRepository = warehouseRepository;
		this.itemRepository = itemRepository;
		this.stockLedger = stockLedger;
		this.entityManager = entityManager;
	}

	public List<Summary> search(@Nullable String keyword, @Nullable List<SalesOrderStatus> statuses,
			@Nullable Long customerId, @Nullable LocalDate fromDate, @Nullable LocalDate toDate) {
		Set<SalesOrderStatus> statusFilter = statuses == null || statuses.isEmpty()
				? EnumSet.allOf(SalesOrderStatus.class)
				: EnumSet.copyOf(statuses);
		return salesOrderRepository
				.search(LikePatterns.contains(keyword), statusFilter, customerId, fromDate, toDate)
				.stream().map(Summary::from).toList();
	}

	public Response get(Long id) {
		return Response.from(find(id));
	}

	@Transactional
	public Response create(CreateRequest request) {
		checkDates(request.orderDate(), request.dueDate());
		Partner customer = usableCustomer(request.customerId());
		Warehouse warehouse = usableWarehouse(request.warehouseId());
		List<LineSpec> lines = lineSpecs(request.lines());
		String orderNo = "SO-%06d".formatted(salesOrderRepository.nextOrderNo());
		SalesOrder so = new SalesOrder(orderNo, customer, warehouse, request.orderDate(), request.dueDate(),
				request.memo(), lines, CurrentUser.id());
		return Response.from(salesOrderRepository.save(so));
	}

	@Transactional
	public Response update(Long id, UpdateRequest request) {
		SalesOrder so = find(id);
		so.checkVersion(request.version());
		checkDates(request.orderDate(), request.dueDate());
		Partner customer = usableCustomer(request.customerId());
		Warehouse warehouse = usableWarehouse(request.warehouseId());
		List<LineSpec> lines = lineSpecs(request.lines());
		// 줄만 바뀌어도 머리 버전을 올린다 (PurchaseOrderService.update 참고)
		entityManager.lock(so, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
		so.change(customer, warehouse, request.orderDate(), request.dueDate(), request.memo(), lines);
		return flushAndRespond(so);
	}

	/**
	 * 확정: 가용 재고를 확인하고 할당한다.
	 * 잠그는 순서: 수주 행(버전 확인 + 잠금) → 현재고 행들(품목 id 순). 입고·출고·조정도 같은 방향으로만 잠근다.
	 */
	@Transactional
	public Response confirm(Long id, ActionRequest request) {
		SalesOrder so = lockForStockChange(id, request.version());
		checkMastersUsable(so);
		stockLedger.allocate(so.confirm(CurrentUser.id()));
		return flushAndRespond(so);
	}

	@Transactional
	public Response cancel(Long id, ReasonRequest request) {
		SalesOrder so = lockForStockChange(id, request.version());
		release(so.cancel(request.reason().strip(), CurrentUser.id()));
		return flushAndRespond(so);
	}

	@Transactional
	public Response close(Long id, ReasonRequest request) {
		SalesOrder so = lockForStockChange(id, request.version());
		release(so.close(request.reason().strip(), CurrentUser.id()));
		return flushAndRespond(so);
	}

	/** 작성 중 취소처럼 풀 할당이 없으면 현재고를 잠그지도 않는다 */
	private void release(List<StockAllocation> releases) {
		if (!releases.isEmpty()) {
			stockLedger.allocate(releases);
		}
	}

	/**
	 * 화면이 본 버전을 확인한 뒤 행을 잠그고 버전을 올린다.
	 * 확인과 잠금 사이에 다른 트랜잭션이 이 수주를 바꿨다면 잠금의 "update ... where version = ?"가 0건이 되어
	 * 409 CONCURRENT_MODIFICATION이 난다. 같은 수주를 두 번 동시에 확정해도 할당은 한 번만 된다.
	 */
	private SalesOrder lockForStockChange(Long id, long version) {
		SalesOrder so = find(id);
		so.checkVersion(version);
		entityManager.lock(so, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
		return so;
	}

	private static void checkDates(LocalDate orderDate, @Nullable LocalDate dueDate) {
		if (dueDate != null && dueDate.isBefore(orderDate)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"납기 요청일(%s)은 수주일(%s)보다 빠를 수 없습니다.".formatted(dueDate, orderDate));
		}
	}

	private void checkMastersUsable(SalesOrder so) {
		checkCustomer(so.getCustomer());
		checkWarehouse(so.getWarehouse());
		so.getLines().forEach(line -> checkItem(line.getItem()));
	}

	/** 같은 품목을 두 줄로 넣지 못하게 막는다. 할당·출고에서 어느 줄의 몫인지 헷갈리지 않게 */
	private List<LineSpec> lineSpecs(List<LineRequest> requests) {
		Set<Long> seen = new HashSet<>();
		for (LineRequest request : requests) {
			if (!seen.add(request.itemId())) {
				throw new BusinessException(ErrorCode.DUPLICATE_LINE_ITEM,
						"같은 품목을 두 줄로 넣을 수 없습니다. 한 줄의 수량을 늘리세요. (품목 id=" + request.itemId() + ")");
			}
		}
		Map<Long, Item> items = itemRepository.findAllById(seen).stream()
				.collect(Collectors.toMap(Item::getId, Function.identity()));
		return requests.stream().map(request -> {
			Item item = items.get(request.itemId());
			if (item == null) {
				throw new BusinessException(ErrorCode.NOT_FOUND, "품목을 찾을 수 없습니다. (id=" + request.itemId() + ")");
			}
			checkItem(item);
			if (!item.getUnit().allows(request.quantity())) {
				throw new BusinessException(ErrorCode.INVALID_QUANTITY_FOR_UNIT, "%s(%s)은(는) %s 단위라서 정수 수량만 입력할 수 있습니다. (입력 %s)"
						.formatted(item.getCode(), item.getName(), item.getUnit(), request.quantity().toPlainString()));
			}
			return new LineSpec(item, request.quantity(), request.unitPrice());
		}).toList();
	}

	private Partner usableCustomer(Long id) {
		Partner partner = partnerRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "거래처를 찾을 수 없습니다. (id=" + id + ")"));
		checkCustomer(partner);
		return partner;
	}

	private Warehouse usableWarehouse(Long id) {
		Warehouse warehouse = warehouseRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "창고를 찾을 수 없습니다. (id=" + id + ")"));
		checkWarehouse(warehouse);
		return warehouse;
	}

	private static void checkCustomer(Partner partner) {
		if (!partner.isActive()) {
			throw new BusinessException(ErrorCode.INACTIVE_MASTER,
					"사용 중지된 거래처입니다: %s %s".formatted(partner.getCode(), partner.getName()));
		}
		if (!partner.hasRole(PartnerRole.CUSTOMER)) {
			throw new BusinessException(ErrorCode.NOT_A_CUSTOMER,
					"%s %s은(는) 고객이 아닙니다. 거래처 화면에서 고객 구분을 추가하세요.".formatted(partner.getCode(), partner.getName()));
		}
	}

	private static void checkWarehouse(Warehouse warehouse) {
		if (!warehouse.isActive()) {
			throw new BusinessException(ErrorCode.INACTIVE_MASTER,
					"사용 중지된 창고입니다: %s %s".formatted(warehouse.getCode(), warehouse.getName()));
		}
	}

	private static void checkItem(Item item) {
		if (!item.isActive()) {
			throw new BusinessException(ErrorCode.INACTIVE_MASTER,
					"사용 중지된 품목입니다: %s %s".formatted(item.getCode(), item.getName()));
		}
	}

	private Response flushAndRespond(SalesOrder so) {
		salesOrderRepository.flush();
		return Response.from(so);
	}

	private SalesOrder find(Long id) {
		return salesOrderRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "수주를 찾을 수 없습니다. (id=" + id + ")"));
	}
}
