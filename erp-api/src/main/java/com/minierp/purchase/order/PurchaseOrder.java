package com.minierp.purchase.order;

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
import com.minierp.master.item.Item;
import com.minierp.master.partner.Partner;
import com.minierp.master.warehouse.Warehouse;
import com.minierp.purchase.order.PurchaseOrderEvent.Type;

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
 * 발주서 (집합 루트). 머리 + 줄 + 상태 이력을 한 덩어리로 다룬다.
 *
 * 상태는 setter로 바꾸지 않는다. submit(), approve(), receive()처럼 업무 행위 메서드만 상태를 바꾸고,
 * 모든 전이는 transition()을 지나가며 PurchaseOrderStatus의 전이 규칙 검사와 이력 기록을 함께 한다.
 * 그래서 "상태는 바뀌었는데 이력이 없다"거나 "작성 중이 아닌데 줄이 바뀌었다"는 일이 생기지 않는다.
 */
@Entity
public class PurchaseOrder extends BaseEntity {

	/** 줄을 만들거나 바꿀 때 넘기는 값. 품목은 Service가 조회해서 사용 가능한지 확인한 뒤 넘긴다 */
	public record LineSpec(Item item, BigDecimal quantity, BigDecimal unitPrice) {
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, updatable = false, length = 20)
	private String orderNo;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "supplier_id", nullable = false)
	private Partner supplier;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "warehouse_id", nullable = false)
	private Warehouse warehouse;

	@Column(nullable = false)
	private LocalDate orderDate;

	private @Nullable LocalDate dueDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PurchaseOrderStatus status;

	@Column(length = 500)
	private @Nullable String memo;

	/**
	 * 낙관적 락. UPDATE할 때마다 1씩 오르고, Hibernate는 "update ... where id = ? and version = ?"로 실행한다.
	 * 그사이 다른 트랜잭션이 먼저 바꿨으면 0건이 바뀌어 ObjectOptimisticLockingFailureException이 난다.
	 * 화면에는 "같은지"만 의미가 있다. 한 번 저장에 1이 오를 수도 2가 오를 수도 있다 (PurchaseOrderService.update 참고).
	 */
	@Version
	private long version;

	@OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("lineNo")
	private List<PurchaseOrderLine> lines = new ArrayList<>();

	@OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.PERSIST)
	@OrderBy("id")
	private List<PurchaseOrderEvent> events = new ArrayList<>();

	protected PurchaseOrder() {
	}

	public PurchaseOrder(String orderNo, Partner supplier, Warehouse warehouse, LocalDate orderDate,
			@Nullable LocalDate dueDate, @Nullable String memo, List<LineSpec> lines, String actor) {
		this.orderNo = orderNo;
		this.status = PurchaseOrderStatus.DRAFT;
		apply(supplier, warehouse, orderDate, dueDate, memo, lines);
		record(Type.CREATED, null, PurchaseOrderStatus.DRAFT, null, actor);
	}

	/** 작성 중(DRAFT)에만 고칠 수 있다. 승인 요청 뒤에 고치려면 반려받아 작성 중으로 되돌린다 */
	public void change(Partner supplier, Warehouse warehouse, LocalDate orderDate, @Nullable LocalDate dueDate,
			@Nullable String memo, List<LineSpec> lines) {
		if (!status.isEditable()) {
			throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
					"%s 상태의 발주는 고칠 수 없습니다. 작성 중일 때만 고칠 수 있습니다.".formatted(status.label()));
		}
		apply(supplier, warehouse, orderDate, dueDate, memo, lines);
	}

	public void submit(String actor) {
		transition(PurchaseOrderStatus.SUBMITTED, Type.SUBMITTED, null, actor);
	}

	/**
	 * 직무 분리: 발주를 만든 사람이나 승인을 요청한 사람은 승인할 수 없다.
	 * 한 사람이 발주를 만들고 스스로 승인하면 "아는 업체에 비싸게 발주"해도 아무도 모른다. 승인은 두 번째 눈이다.
	 */
	public void approve(String actor) {
		if (actor.equals(getCreatedBy()) || actor.equals(lastSubmitter())) {
			throw new BusinessException(ErrorCode.SELF_APPROVAL_NOT_ALLOWED,
					"본인이 작성하거나 승인 요청한 발주는 승인할 수 없습니다. (작성 %s, 승인 요청 %s)"
							.formatted(getCreatedBy(), lastSubmitter()));
		}
		transition(PurchaseOrderStatus.APPROVED, Type.APPROVED, null, actor);
	}

	/** 반려: 작성 중으로 되돌려 다시 고치게 한다. 왜 반려했는지가 남아야 고칠 수 있으므로 사유가 필수다 */
	public void reject(String reason, String actor) {
		transition(PurchaseOrderStatus.DRAFT, Type.REJECTED, reason, actor);
	}

	/** 취소: 입고가 하나라도 있으면 취소할 수 없다 (받은 물건이 없던 일이 되지 않는다). 그때는 마감한다 */
	public void cancel(String reason, String actor) {
		if (hasReceipts()) {
			throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
					"이미 입고된 발주는 취소할 수 없습니다. 더 받지 않으려면 마감하세요.");
		}
		transition(PurchaseOrderStatus.CANCELED, Type.CANCELED, reason, actor);
	}

	/** 잔량 마감: 덜 받았지만 남은 수량은 더 받지 않는다 */
	public void close(String reason, String actor) {
		transition(PurchaseOrderStatus.CLOSED, Type.CLOSED, reason, actor);
	}

	/**
	 * 입고: 줄별로 받은 수량을 더하고, 전부 받았으면 입고 완료, 아니면 부분 입고가 된다.
	 * 줄 하나라도 남은 수량을 넘으면 예외가 나고 트랜잭션 전체가 롤백된다 (앞 줄만 반영되는 일은 없다).
	 *
	 * @param quantities 발주 줄 id → 이번에 받은 수량
	 */
	public void receive(Map<Long, BigDecimal> quantities, String receiptNo, String actor) {
		if (!status.isReceivable()) {
			throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
					"%s 상태의 발주는 입고할 수 없습니다. 승인된 발주만 입고할 수 있습니다.".formatted(status.label()));
		}
		quantities.forEach((lineId, quantity) -> line(lineId).receive(quantity));
		PurchaseOrderStatus next = lines.stream().allMatch(PurchaseOrderLine::isFullyReceived)
				? PurchaseOrderStatus.RECEIVED
				: PurchaseOrderStatus.PARTIALLY_RECEIVED;
		transition(next, Type.RECEIVED, receiptNo, actor);
	}

	public PurchaseOrderLine line(Long lineId) {
		return lines.stream().filter(line -> line.getId().equals(lineId)).findFirst()
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT,
						"발주 %s에 없는 줄입니다. (줄 id=%d)".formatted(orderNo, lineId)));
	}

	public boolean hasReceipts() {
		return lines.stream().anyMatch(line -> line.getReceivedQuantity().signum() > 0);
	}

	public BigDecimal totalAmount() {
		return lines.stream().map(PurchaseOrderLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	/** 화면이 본 버전과 지금 버전이 다르면, 화면을 연 뒤 다른 사람이 이 발주를 바꾼 것이다 */
	public void checkVersion(long expected) {
		if (version != expected) {
			throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION,
					"발주 %s을(를) 다른 사용자가 먼저 변경했습니다. (화면 버전 %d, 현재 버전 %d) 다시 조회한 뒤 시도하세요."
							.formatted(orderNo, expected, version));
		}
	}

	private void transition(PurchaseOrderStatus target, Type type, @Nullable String reason, String actor) {
		if (!status.canTransitionTo(target)) {
			throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
					"%s 상태의 발주는 %s(으)로 바꿀 수 없습니다.".formatted(status.label(), target.label()));
		}
		PurchaseOrderStatus from = status;
		this.status = target;
		record(type, from, target, reason, actor);
	}

	private void record(Type type, @Nullable PurchaseOrderStatus from, PurchaseOrderStatus to, @Nullable String reason,
			String actor) {
		events.add(new PurchaseOrderEvent(this, type, from, to, reason, actor, Instant.now()));
	}

	private @Nullable String lastSubmitter() {
		String submitter = null;
		for (PurchaseOrderEvent event : events) {
			if (event.getType() == Type.SUBMITTED) {
				submitter = event.getActor();
			}
		}
		return submitter;
	}

	private void apply(Partner supplier, Warehouse warehouse, LocalDate orderDate, @Nullable LocalDate dueDate,
			@Nullable String memo, List<LineSpec> specs) {
		if (specs.isEmpty()) {
			throw new IllegalArgumentException("발주 줄이 하나 이상 있어야 합니다.");
		}
		if (dueDate != null && dueDate.isBefore(orderDate)) {
			throw new IllegalArgumentException("납기 요청일은 발주일보다 빠를 수 없습니다.");
		}
		this.supplier = supplier;
		this.warehouse = warehouse;
		this.orderDate = orderDate;
		this.dueDate = dueDate;
		this.memo = memo == null || memo.isBlank() ? null : memo.strip();
		replaceLines(specs);
	}

	/**
	 * 줄 전체를 화면이 보낸 목록으로 바꾼다. 같은 줄 번호의 기존 줄은 지우고 새로 만들지 않고 그 자리에서 고친다.
	 *
	 * lines.clear() 후 새 줄을 add하면 Hibernate는 flush할 때 INSERT를 DELETE보다 먼저 실행한다 (flush 순서: INSERT → UPDATE → DELETE).
	 * 그러면 새 1번 줄의 INSERT가 아직 지워지지 않은 옛 1번 줄과 부딪혀 uk_purchase_order_line 제약 위반이 난다 (직접 재현함).
	 * 남는 줄은 UPDATE, 늘어난 줄은 INSERT, 줄어든 줄은 DELETE가 되도록 줄 번호 순서대로 맞춘다.
	 */
	private void replaceLines(List<LineSpec> specs) {
		for (int i = 0; i < specs.size(); i++) {
			LineSpec spec = specs.get(i);
			if (i < lines.size()) {
				lines.get(i).change(spec.item(), spec.quantity(), spec.unitPrice());
			} else {
				lines.add(new PurchaseOrderLine(this, i + 1, spec.item(), spec.quantity(), spec.unitPrice()));
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

	public Partner getSupplier() {
		return supplier;
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

	public PurchaseOrderStatus getStatus() {
		return status;
	}

	public @Nullable String getMemo() {
		return memo;
	}

	public long getVersion() {
		return version;
	}

	public List<PurchaseOrderLine> getLines() {
		return Collections.unmodifiableList(lines);
	}

	public List<PurchaseOrderEvent> getEvents() {
		return Collections.unmodifiableList(events);
	}
}
