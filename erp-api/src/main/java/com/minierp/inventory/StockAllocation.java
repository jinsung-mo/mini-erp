package com.minierp.inventory;

import java.math.BigDecimal;

import com.minierp.master.item.Item;
import com.minierp.master.warehouse.Warehouse;

/**
 * 할당 변경 하나. 수주 확정은 +, 취소·마감은 남은 수량만큼 -.
 * 할당은 실물이 움직인 사건이 아니라 "약속"이라서 원장에 쌓지 않는다. 대신 진행 중인 수주 줄에서 언제든 다시 계산할 수 있다.
 *
 * @param quantity 늘리면 +, 풀면 -
 */
public record StockAllocation(Item item, Warehouse warehouse, BigDecimal quantity) {
}
