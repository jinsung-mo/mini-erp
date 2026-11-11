package com.minierp.inventory;

import java.math.BigDecimal;

import com.minierp.common.error.BusinessException;
import com.minierp.common.error.ErrorCode;
import com.minierp.common.jpa.BaseEntity;
import com.minierp.master.item.Item;
import com.minierp.master.warehouse.Warehouse;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

/**
 * 현재고: 품목 × 창고마다 한 줄. 수불 원장을 합한 값을 미리 저장해 둔 것이다.
 *
 * 값은 {@link StockLedger}만 바꾼다. 원장 한 줄을 쓰면서 같은 트랜잭션에서 이 값도 함께 바꾼다.
 * 그래서 이 엔티티에는 수량을 정하는 메서드가 없고, 더하거나 빼는 apply()만 있다.
 * 줄은 네이티브 INSERT ... ON CONFLICT로 만든다 (StockRepository.insertIfAbsent). JPA 기본 생성자는 조회용이다.
 */
@Entity
public class Stock extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "item_id", nullable = false, updatable = false)
	private Item item;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "warehouse_id", nullable = false, updatable = false)
	private Warehouse warehouse;

	@Column(nullable = false, precision = 15, scale = 3)
	private BigDecimal quantity;

	protected Stock() {
	}

	/**
	 * 들어오면 +, 나가면 -. 결과가 음수면 막는다 (DB의 ck_stock_quantity가 마지막으로 한 번 더 막는다).
	 * @return 바뀐 뒤 수량
	 */
	BigDecimal apply(BigDecimal delta) {
		BigDecimal next = quantity.add(delta);
		if (next.signum() < 0) {
			throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK,
					"%s(%s)의 %s 재고가 부족합니다. (현재 %s, 요청 %s)".formatted(item.getCode(), item.getName(),
							warehouse.getName(), quantity.stripTrailingZeros().toPlainString(),
							delta.stripTrailingZeros().toPlainString()));
		}
		quantity = next;
		return next;
	}

	public Long getId() {
		return id;
	}

	public Item getItem() {
		return item;
	}

	public Warehouse getWarehouse() {
		return warehouse;
	}

	public BigDecimal getQuantity() {
		return quantity;
	}
}
