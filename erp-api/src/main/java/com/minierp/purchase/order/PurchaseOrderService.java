package com.minierp.purchase.order;

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
import com.minierp.master.item.Item;
import com.minierp.master.item.ItemRepository;
import com.minierp.master.partner.Partner;
import com.minierp.master.partner.PartnerRepository;
import com.minierp.master.partner.PartnerRole;
import com.minierp.master.warehouse.Warehouse;
import com.minierp.master.warehouse.WarehouseRepository;
import com.minierp.purchase.order.PurchaseOrder.LineSpec;
import com.minierp.purchase.order.PurchaseOrderDtos.ActionRequest;
import com.minierp.purchase.order.PurchaseOrderDtos.CreateRequest;
import com.minierp.purchase.order.PurchaseOrderDtos.LineRequest;
import com.minierp.purchase.order.PurchaseOrderDtos.ReasonRequest;
import com.minierp.purchase.order.PurchaseOrderDtos.Response;
import com.minierp.purchase.order.PurchaseOrderDtos.Summary;
import com.minierp.purchase.order.PurchaseOrderDtos.UpdateRequest;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

/**
 * 발주 업무 규칙과 트랜잭션 경계.
 *
 * 상태 전이 규칙은 엔티티(PurchaseOrder)와 PurchaseOrderStatus가 지킨다. Service는 그 바깥의 일을 한다:
 * 기준정보를 조회해서 쓸 수 있는지 확인하고(사용 중, 공급처 역할), 화면이 본 버전을 확인하고, 작업자를 넘긴다.
 */
@Service
@Transactional(readOnly = true)
public class PurchaseOrderService {

	private final PurchaseOrderRepository purchaseOrderRepository;
	private final PartnerRepository partnerRepository;
	private final WarehouseRepository warehouseRepository;
	private final ItemRepository itemRepository;
	private final EntityManager entityManager;

	public PurchaseOrderService(PurchaseOrderRepository purchaseOrderRepository, PartnerRepository partnerRepository,
			WarehouseRepository warehouseRepository, ItemRepository itemRepository, EntityManager entityManager) {
		this.purchaseOrderRepository = purchaseOrderRepository;
		this.partnerRepository = partnerRepository;
		this.warehouseRepository = warehouseRepository;
		this.itemRepository = itemRepository;
		this.entityManager = entityManager;
	}

	public List<Summary> search(@Nullable String keyword, @Nullable List<PurchaseOrderStatus> statuses,
			@Nullable Long supplierId, @Nullable LocalDate fromDate, @Nullable LocalDate toDate) {
		// "in ()"는 SQL 문법 오류라서, 상태 조건이 없으면 빈 목록 대신 전체 상태를 넘긴다
		Set<PurchaseOrderStatus> statusFilter = statuses == null || statuses.isEmpty()
				? EnumSet.allOf(PurchaseOrderStatus.class)
				: EnumSet.copyOf(statuses);
		return purchaseOrderRepository
				.search(LikePatterns.contains(keyword), statusFilter, supplierId, fromDate, toDate)
				.stream().map(Summary::from).toList();
	}

	public Response get(Long id) {
		return Response.from(find(id));
	}

	@Transactional
	public Response create(CreateRequest request) {
		checkDates(request.orderDate(), request.dueDate());
		Partner supplier = usableSupplier(request.supplierId());
		Warehouse warehouse = usableWarehouse(request.warehouseId());
		List<LineSpec> lines = lineSpecs(request.lines());
		String orderNo = "PO-%06d".formatted(purchaseOrderRepository.nextOrderNo());
		PurchaseOrder po = new PurchaseOrder(orderNo, supplier, warehouse, request.orderDate(), request.dueDate(),
				request.memo(), lines, CurrentUser.id());
		return Response.from(purchaseOrderRepository.save(po));
	}

	@Transactional
	public Response update(Long id, UpdateRequest request) {
		PurchaseOrder po = find(id);
		po.checkVersion(request.version());
		checkDates(request.orderDate(), request.dueDate());
		Partner supplier = usableSupplier(request.supplierId());
		Warehouse warehouse = usableWarehouse(request.warehouseId());
		List<LineSpec> lines = lineSpecs(request.lines());
		// 줄의 수량·단가만 바뀌면 UPDATE는 purchase_order_line에만 나가고 머리의 version은 그대로다 (직접 재현함).
		// 그러면 옛 화면이 같은 버전으로 저장해 방금 고친 내용을 덮어쓴다. 그래서 내용을 고칠 때는 머리 버전을 직접 올린다.
		// PESSIMISTIC_FORCE_INCREMENT: 행을 잠그고 그 자리에서 "update ... set version = v + 1 where version = v"를 실행한다.
		// 머리 값(메모 등)도 바뀌면 flush 때 한 번 더 올라 +2가 된다. 화면은 같은지만 비교하므로 문제없다
		entityManager.lock(po, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
		po.change(supplier, warehouse, request.orderDate(), request.dueDate(), request.memo(), lines);
		return flushAndRespond(po);
	}

	/** 작성 중에 저장해 둔 뒤 기준정보가 사용 중지됐을 수 있으므로, 승인 요청·승인할 때 다시 확인한다 */
	@Transactional
	public Response submit(Long id, ActionRequest request) {
		PurchaseOrder po = find(id);
		po.checkVersion(request.version());
		checkMastersUsable(po);
		po.submit(CurrentUser.id());
		return flushAndRespond(po);
	}

	@Transactional
	public Response approve(Long id, ActionRequest request) {
		PurchaseOrder po = find(id);
		po.checkVersion(request.version());
		checkMastersUsable(po);
		po.approve(CurrentUser.id());
		return flushAndRespond(po);
	}

	@Transactional
	public Response reject(Long id, ReasonRequest request) {
		PurchaseOrder po = find(id);
		po.checkVersion(request.version());
		po.reject(request.reason().strip(), CurrentUser.id());
		return flushAndRespond(po);
	}

	@Transactional
	public Response cancel(Long id, ReasonRequest request) {
		PurchaseOrder po = find(id);
		po.checkVersion(request.version());
		po.cancel(request.reason().strip(), CurrentUser.id());
		return flushAndRespond(po);
	}

	@Transactional
	public Response close(Long id, ReasonRequest request) {
		PurchaseOrder po = find(id);
		po.checkVersion(request.version());
		po.close(request.reason().strip(), CurrentUser.id());
		return flushAndRespond(po);
	}

	private static void checkDates(LocalDate orderDate, @Nullable LocalDate dueDate) {
		if (dueDate != null && dueDate.isBefore(orderDate)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"납기 요청일(%s)은 발주일(%s)보다 빠를 수 없습니다.".formatted(dueDate, orderDate));
		}
	}

	private void checkMastersUsable(PurchaseOrder po) {
		checkSupplier(po.getSupplier());
		checkWarehouse(po.getWarehouse());
		po.getLines().forEach(line -> checkItem(line.getItem()));
	}

	/**
	 * 화면이 보낸 줄 → 엔티티에 넘길 줄. 품목은 한 번에 조회한다 (줄마다 findById를 부르면 줄 수만큼 쿼리가 나간다).
	 * 같은 품목을 두 줄로 넣으면 입고·재고에서 어느 줄의 수량인지 헷갈리므로 막는다. 수량을 늘리려면 한 줄의 수량을 고친다.
	 */
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

	private Partner usableSupplier(Long id) {
		Partner partner = partnerRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "거래처를 찾을 수 없습니다. (id=" + id + ")"));
		checkSupplier(partner);
		return partner;
	}

	private Warehouse usableWarehouse(Long id) {
		Warehouse warehouse = warehouseRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "창고를 찾을 수 없습니다. (id=" + id + ")"));
		checkWarehouse(warehouse);
		return warehouse;
	}

	private static void checkSupplier(Partner partner) {
		if (!partner.isActive()) {
			throw new BusinessException(ErrorCode.INACTIVE_MASTER,
					"사용 중지된 거래처입니다: %s %s".formatted(partner.getCode(), partner.getName()));
		}
		if (!partner.hasRole(PartnerRole.SUPPLIER)) {
			throw new BusinessException(ErrorCode.NOT_A_SUPPLIER,
					"%s %s은(는) 공급처가 아닙니다. 거래처 화면에서 공급처 구분을 추가하세요.".formatted(partner.getCode(), partner.getName()));
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

	/** 수정자·수정일시와 버전은 flush(UPDATE 실행) 때 바뀌므로, 응답을 만들기 전에 flush한다 */
	private Response flushAndRespond(PurchaseOrder po) {
		purchaseOrderRepository.flush();
		return Response.from(po);
	}

	private PurchaseOrder find(Long id) {
		return purchaseOrderRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "발주를 찾을 수 없습니다. (id=" + id + ")"));
	}
}
