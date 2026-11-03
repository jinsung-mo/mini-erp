package com.minierp.master.item;

/**
 * 재고 관리 단위.
 * 값을 추가하면 DB의 CHECK 제약(ck_item_unit)도 새 마이그레이션으로 함께 바꿔야 한다.
 */
public enum Unit {
	EA, BOX, KG, L
}
