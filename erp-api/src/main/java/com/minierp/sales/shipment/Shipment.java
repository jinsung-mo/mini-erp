package com.minierp.sales.shipment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.minierp.common.jpa.BaseEntity;
import com.minierp.master.warehouse.Warehouse;
import com.minierp.sales.order.SalesOrder;

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
 * 출고: 고객에게 물건을 실제로 내보낸 사건. 한 수주를 여러 번 나눠 내보낼 수 있다.
 * 입고와 마찬가지로 고치거나 지우지 않는다. 잘못 내보냈으면 반품 같은 반대 거래로 바로잡는다 (이 프로젝트 범위 밖).
 */
@Entity
public class Shipment extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, updatable = false, length = 20)
	private String shipmentNo;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "sales_order_id", nullable = false, updatable = false)
	private SalesOrder salesOrder;

	/** 내보낸 창고. 수주의 출고 창고를 복사해 둔다 (원장의 기준) */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "warehouse_id", nullable = false, updatable = false)
	private Warehouse warehouse;

	@Column(nullable = false, updatable = false)
	private LocalDate shipDate;

	@Column(updatable = false, length = 500)
	private @Nullable String memo;

	@OneToMany(mappedBy = "shipment", cascade = CascadeType.PERSIST)
	@OrderBy("id")
	private List<ShipmentLine> lines = new ArrayList<>();

	protected Shipment() {
	}

	/** @param quantities 수주 줄 id → 내보낸 수량 (요청 순서대로) */
	Shipment(String shipmentNo, SalesOrder salesOrder, LocalDate shipDate, @Nullable String memo,
			Map<Long, BigDecimal> quantities) {
		this.shipmentNo = shipmentNo;
		this.salesOrder = salesOrder;
		this.warehouse = salesOrder.getWarehouse();
		this.shipDate = shipDate;
		this.memo = memo == null || memo.isBlank() ? null : memo.strip();
		quantities.forEach((lineId, quantity) ->
				lines.add(new ShipmentLine(this, salesOrder.line(lineId), quantity)));
	}

	public Long getId() {
		return id;
	}

	public String getShipmentNo() {
		return shipmentNo;
	}

	public SalesOrder getSalesOrder() {
		return salesOrder;
	}

	public Warehouse getWarehouse() {
		return warehouse;
	}

	public LocalDate getShipDate() {
		return shipDate;
	}

	public @Nullable String getMemo() {
		return memo;
	}

	public List<ShipmentLine> getLines() {
		return Collections.unmodifiableList(lines);
	}
}
