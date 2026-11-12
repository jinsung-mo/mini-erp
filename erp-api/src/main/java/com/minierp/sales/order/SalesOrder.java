package com.minierp.sales.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.minierp.common.error.BusinessException;
import com.minierp.common.error.ErrorCode;
import com.minierp.common.jpa.BaseEntity;
import com.minierp.inventory.StockAllocation;
import com.minierp.master.item.Item;
import com.minierp.master.partner.Partner;
import com.minierp.master.warehouse.Warehouse;
import com.minierp.sales.order.SalesOrderEvent.Type;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Version;

/**
 * 수주서 (집합 루트). 구조와 규칙은 발주(PurchaseOrder)와 같다: 상태는 업무 행위 메서드로만 바뀌고, 모든 전이는 이력을 남긴다.
 *
 * 다른 점은 상태 전이가 재고 할당을 함께 움직인다는 것이다. 엔티티는 재고를 직접 바꾸지 않는다(그건 StockLedger의 일).
 * 대신 confirm(), cancel(), close()가 "이 전이로 할당이 얼마나 바뀌어야 하는지"를 돌려주고, Service가 같은 트랜잭션에서 StockLedger에 넘긴다.
 * 할당 규칙(어느 상태가 할당을 붙잡는지, 얼마를 풀어야 하는지)이 상태 규칙 옆에 있어야 둘이 어긋나지 않는다.
 */
@Entity
public class SalesOrder extends BaseEntity {

	public record LineSpec(Item item, BigDecimal quantity, BigDecimal unitPrice) {
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, updatable = false, length = 20)
	private String orderNo;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "customer_id", nullable = false)
	private Partner customer;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "warehouse_id", nullable = false)
	private Warehouse warehouse;

	@Column(nullable = false)
	private LocalDate orderDate;

	private @Nullable LocalDate dueDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SalesOrderStatus status;

	@Column(length = 500)
	private @Nullable String memo;

	@Version
	private long version;

	@OneToMany(mappedBy = "salesOrder", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("lineNo")
	private List<SalesOrderLine> lines = new ArrayList<>();

	@OneToMany(mappedBy = "salesOrder", cascade = CascadeType.PERSIST)
	@OrderBy("id")
	private List<SalesOrderEvent> events = new ArrayList<>();

	protected SalesOrder() {
	}

	public SalesOrder(String orderNo, Partner customer, Warehouse warehouse, LocalDate orderDate,
			@Nullable LocalDate dueDate, @Nullable String memo, List<LineSpec> lines, String actor) {
		this.orderNo = orderNo;
		this.status = SalesOrderStatus.DRAFT;
		apply(customer, warehouse, orderDate, dueDate, memo, lines);
		record(Type.CREATED, null, SalesOrderStatus.DRAFT, null, actor);
	}

	/** 작성 중(DRAFT)에만 고칠 수 있다. 확정 뒤에는 이미 재고를 약속했으므로, 바꾸려면 취소하고 새로 만든다 */
	public void change(Partner customer, Warehouse warehouse, LocalDate orderDate, @Nullable LocalDate dueDate,
			@Nullable String memo, List<LineSpec> lines) {
		if (!status.isEditable()) {
			throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
					"%s 상태의 수주는 고칠 수 없습니다. 작성 중일 때만 고칠 수 있습니다.".formatted(status.label()));
		}
		apply(customer, warehouse, orderDate, dueDate, memo, lines);
	}

	/** 확정: 줄 수량 전부를 할당해야 한다. 돌려준 할당을 StockLedger가 가용 재고와 비교하고, 모자라면 트랜잭션 전체가 롤백된다 */
	public List<StockAllocation> confirm(String actor) {
		transition(SalesOrderStatus.CONFIRMED, Type.CONFIRMED, null, actor);
		return remainingAllocations(BigDecimal.ONE);
	}

	/** 취소: 출고가 하나라도 있으면 취소할 수 없다 (나간 물건이 없던 일이 되지 않는다). 확정 상태였다면 할당을 전부 푼다 */
	public List<StockAllocation> cancel(String reason, String actor) {
		if (hasShipments()) {
			throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
					"이미 출고한 수주는 취소할 수 없습니다. 더 내보내지 않으려면 마감하세요.");
		}
		return releaseAndTransition(SalesOrderStatus.CANCELED, Type.CANCELED, reason, actor);
	}

	/** 잔량 마감: 남은 수량은 내보내지 않는다. 남은 수량만큼 붙잡고 있던 할당을 푼다 */
	public List<StockAllocation> close(String reason, String actor) {
		return releaseAndTransition(SalesOrderStatus.CLOSED, Type.CLOSED, reason, actor);
	}

	/**
	 * 출고: 줄별로 내보낸 수량을 더한다. 할당은 여기서 풀지 않는다. 출고 원장(SHIPMENT)이 현재고와 할당을 같은 수량만큼 함께 줄인다.
	 *
	 * @param quantities 수주 줄 id → 이번에 내보낸 수량
	 */
	public void ship(Map<Long, BigDecimal> quantities, String shipmentNo, String actor) {
		if (!status.isShippable()) {
			throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
					"%s 상태의 수주는 출고할 수 없습니다. 확정된 수주만 출고할 수 있습니다.".formatted(status.label()));
		}
		quantities.forEach((lineId, quantity) -> line(lineId).ship(quantity));
		SalesOrderStatus next = lines.stream().allMatch(SalesOrderLine::isFullyShipped)
				? SalesOrderStatus.SHIPPED
				: SalesOrderStatus.PARTIALLY_SHIPPED;
		transition(next, Type.SHIPPED, shipmentNo, actor);
	}

	public SalesOrderLine line(Long lineId) {
		return lines.stream().filter(line -> line.getId().equals(lineId)).findFirst()
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT,
						"수주 %s에 없는 줄입니다. (줄 id=%d)".formatted(orderNo, lineId)));
	}

	public boolean hasShipments() {
		return lines.stream().anyMatch(line -> line.getShippedQuantity().signum() > 0);
	}

	public BigDecimal totalAmount() {
		return lines.stream().map(SalesOrderLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	public void checkVersion(long expected) {
		if (version != expected) {
			throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION,
					"수주 %s을(를) 다른 사용자가 먼저 변경했습니다. (화면 버전 %d, 현재 버전 %d) 다시 조회한 뒤 시도하세요."
							.formatted(orderNo, expected, version));
		}
	}

	/** 할당을 붙잡고 있던 상태에서 끝낼 때만 푼다. 작성 중에 취소하면 풀 할당이 없다 */
	private List<StockAllocation> releaseAndTransition(SalesOrderStatus target, Type type, String reason,
			String actor) {
		List<StockAllocation> release = status.holdsAllocation()
				? remainingAllocations(BigDecimal.ONE.negate())
				: List.of();
		transition(target, type, reason, actor);
		return release;
	}

	/** 줄마다 남은 수량 × sign. 다 내보낸 줄은 뺀다 (0 할당은 의미가 없다) */
	private List<StockAllocation> remainingAllocations(BigDecimal sign) {
		return lines.stream()
				.filter(line -> !line.isFullyShipped())
				.map(line -> new StockAllocation(line.getItem(), warehouse, line.remainingQuantity().multiply(sign)))
				.toList();
	}

	private void transition(SalesOrderStatus target, Type type, @Nullable String reason, String actor) {
		if (!status.canTransitionTo(target)) {
			throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
					"%s 상태의 수주는 %s(으)로 바꿀 수 없습니다.".formatted(status.label(), target.label()));
		}
		SalesOrderStatus from = status;
		this.status = target;
		record(type, from, target, reason, actor);
	}

	private void record(Type type, @Nullable SalesOrderStatus from, SalesOrderStatus to, @Nullable String reason,
			String actor) {
		events.add(new SalesOrderEvent(this, type, from, to, reason, actor, Instant.now()));
	}

	private void apply(Partner customer, Warehouse warehouse, LocalDate orderDate, @Nullable LocalDate dueDate,
			@Nullable String memo, List<LineSpec> specs) {
		if (specs.isEmpty()) {
			throw new IllegalArgumentException("수주 줄이 하나 이상 있어야 합니다.");
		}
		if (dueDate != null && dueDate.isBefore(orderDate)) {
			throw new IllegalArgumentException("납기 요청일은 수주일보다 빠를 수 없습니다.");
		}
		this.customer = customer;
		this.warehouse = warehouse;
		this.orderDate = orderDate;
		this.dueDate = dueDate;
		this.memo = memo == null || memo.isBlank() ? null : memo.strip();
		replaceLines(specs);
	}

	/** 줄 번호 순서대로 제자리에서 고친다. 이유는 PurchaseOrder.replaceLines 참고 (flush 순서 INSERT → DELETE) */
	private void replaceLines(List<LineSpec> specs) {
		for (int i = 0; i < specs.size(); i++) {
			LineSpec spec = specs.get(i);
			if (i < lines.size()) {
				lines.get(i).change(spec.item(), spec.quantity(), spec.unitPrice());
			} else {
				lines.add(new SalesOrderLine(this, i + 1, spec.item(), spec.quantity(), spec.unitPrice()));
			}
		}
		while (lines.size() > specs.size()) {
			lines.remove(lines.size() - 1);
		}
	}

	public Long getId() {
		return id;
	}

	public String getOrderNo() {
		return orderNo;
	}

	public Partner getCustomer() {
		return customer;
	}

	public Warehouse getWarehouse() {
		return warehouse;
	}

	public LocalDate getOrderDate() {
		return orderDate;
	}

	public @Nullable LocalDate getDueDate() {
		return dueDate;
	}

	public SalesOrderStatus getStatus() {
		return status;
	}

	public @Nullable String getMemo() {
		return memo;
	}

	public long getVersion() {
		return version;
	}

	public List<SalesOrderLine> getLines() {
		return Collections.unmodifiableList(lines);
	}

	public List<SalesOrderEvent> getEvents() {
		return Collections.unmodifiableList(events);
	}
}
