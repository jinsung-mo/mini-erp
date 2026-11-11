package com.minierp.purchase.receipt;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.minierp.common.jpa.BaseEntity;
import com.minierp.master.warehouse.Warehouse;
import com.minierp.purchase.order.PurchaseOrder;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;

/**
 * 입고: 공급처에서 물건이 실제로 들어온 사건. 한 발주를 여러 번 나눠 받을 수 있다.
 *
 * 입고는 고치거나 지우지 않는다. 잘못 받았으면 재고 조정(3단계)이나 이후의 반품 같은 반대 거래로 바로잡는다.
 * 이미 지나간 사건을 고치면 그 사이에 이 입고를 근거로 일어난 일(재고, 대금 지급)의 근거가 사라진다.
 */
@Entity
public class GoodsReceipt extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, updatable = false, length = 20)
	private String receiptNo;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "purchase_order_id", nullable = false, updatable = false)
	private PurchaseOrder purchaseOrder;

	/** 받은 창고. 발주의 입고 창고를 복사해 둔다 (나중에 발주의 창고가 바뀌어도 이 입고가 어디로 들어왔는지는 그대로다) */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "warehouse_id", nullable = false, updatable = false)
	private Warehouse warehouse;

	@Column(nullable = false, updatable = false)
	private LocalDate receiptDate;

	@Column(updatable = false, length = 500)
	private @Nullable String memo;

	@OneToMany(mappedBy = "goodsReceipt", cascade = CascadeType.PERSIST)
	@OrderBy("id")
	private List<GoodsReceiptLine> lines = new ArrayList<>();

	protected GoodsReceipt() {
	}

	/** @param quantities 발주 줄 id → 받은 수량 (요청 순서대로) */
	GoodsReceipt(String receiptNo, PurchaseOrder purchaseOrder, LocalDate receiptDate, @Nullable String memo,
			Map<Long, BigDecimal> quantities) {
		this.receiptNo = receiptNo;
		this.purchaseOrder = purchaseOrder;
		this.warehouse = purchaseOrder.getWarehouse();
		this.receiptDate = receiptDate;
		this.memo = memo == null || memo.isBlank() ? null : memo.strip();
		quantities.forEach((lineId, quantity) ->
				lines.add(new GoodsReceiptLine(this, purchaseOrder.line(lineId), quantity)));
	}

	public Long getId() {
		return id;
	}

	public String getReceiptNo() {
		return receiptNo;
	}

	public PurchaseOrder getPurchaseOrder() {
		return purchaseOrder;
	}

	public Warehouse getWarehouse() {
		return warehouse;
	}

	public LocalDate getReceiptDate() {
		return receiptDate;
	}

	public @Nullable String getMemo() {
		return memo;
	}

	public List<GoodsReceiptLine> getLines() {
		return Collections.unmodifiableList(lines);
	}
}
