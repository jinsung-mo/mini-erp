package com.minierp.purchase.receipt;

import java.math.BigDecimal;

import com.minierp.master.item.Item;
import com.minierp.purchase.order.PurchaseOrderLine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

/**
 * 입고 줄: 어느 발주 줄을 몇 개 받았나.
 * 품목은 발주 줄에서 따라오지만 따로 저장한다. 3단계 재고 원장은 "품목 × 창고"로 쌓이므로 입고 줄만 보고 품목을 알 수 있어야 한다.
 */
@Entity
public class GoodsReceiptLine {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "goods_receipt_id", nullable = false, updatable = false)
	private GoodsReceipt goodsReceipt;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "purchase_order_line_id", nullable = false, updatable = false)
	private PurchaseOrderLine purchaseOrderLine;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "item_id", nullable = false, updatable = false)
	private Item item;

	@Column(nullable = false, updatable = false, precision = 15, scale = 3)
	private BigDecimal quantity;

	protected GoodsReceiptLine() {
	}

	GoodsReceiptLine(GoodsReceipt goodsReceipt, PurchaseOrderLine purchaseOrderLine, BigDecimal quantity) {
		this.goodsReceipt = goodsReceipt;
		this.purchaseOrderLine = purchaseOrderLine;
		this.item = purchaseOrderLine.getItem();
		this.quantity = quantity;
	}

	public Long getId() {
		return id;
	}

	public PurchaseOrderLine getPurchaseOrderLine() {
		return purchaseOrderLine;
	}

	public Item getItem() {
		return item;
	}

	public BigDecimal getQuantity() {
		return quantity;
	}
}
