package com.minierp.master.item;

import java.math.BigDecimal;

/**
 * 재고 관리 단위.
 * 값을 추가하면 DB의 CHECK 제약(ck_item_unit)도 새 마이그레이션으로 함께 바꿔야 한다.
 */
public enum Unit {
	EA(false), BOX(false), KG(true), L(true);

	/** 볼트 2.5개는 없지만 철판 2.5kg은 있다. 셀 수 있는 단위는 정수 수량만 받는다 */
	private final boolean fractionAllowed;

	Unit(boolean fractionAllowed) {
		this.fractionAllowed = fractionAllowed;
	}

	public boolean allows(BigDecimal quantity) {
		return fractionAllowed || quantity.stripTrailingZeros().scale() <= 0;
	}
}
