package com.minierp.sales.shipment;

import java.math.BigDecimal;

import com.minierp.master.item.Item;
import com.minierp.sales.order.SalesOrderLine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

/**
 * 출고 줄: 어느 수주 줄을 몇 개 내보냈나. 원장의 근거(source_line_id)가 출고 줄 id다 (입고 줄과 같은 구조).
 */
@Entity
public class ShipmentLine {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "shipment_id", nullable = false, updatable = false)
	private Shipment shipment;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "sales_order_line_id", nullable = false, updatable = false)
	private SalesOrderLine salesOrderLine;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "item_id", nullable = false, updatable = false)
	private Item item;

	@Column(nullable = false, updatable = false, precision = 15, scale = 3)
	private BigDecimal quantity;

	protected ShipmentLine() {
	}

	ShipmentLine(Shipment shipment, SalesOrderLine salesOrderLine, BigDecimal quantity) {
		this.shipment = shipment;
		this.salesOrderLine = salesOrderLine;
		this.item = salesOrderLine.getItem();
		this.quantity = quantity;
	}

	public Long getId() {
		return id;
	}

	public SalesOrderLine getSalesOrderLine() {
		return salesOrderLine;
	}

	public Item getItem() {
		return item;
	}

	public BigDecimal getQuantity() {
		return quantity;
	}
}
