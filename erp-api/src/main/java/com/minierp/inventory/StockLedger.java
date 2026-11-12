package com.minierp.inventory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.minierp.common.error.BusinessException;
import com.minierp.common.error.ErrorCode;
import com.minierp.common.web.CurrentUser;

/**
 * 재고를 바꾸는 유일한 입구. 원장 한 줄을 쌓고 현재고를 같은 만큼 바꾼다. 할당량(4단계)도 여기서만 바꾼다.
 *
 * Propagation.MANDATORY: 반드시 부르는 쪽의 트랜잭션 안에서만 돈다. 입고 문서는 저장됐는데 재고는 안 바뀐 상태
 * (또는 그 반대)가 생기지 않도록, 원장 기록이 혼자 따로 커밋되는 길을 막는다. 트랜잭션 밖에서 부르면 바로 예외가 난다.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class StockLedger {

	/**
	 * 잠그는 순서. 두 입고가 같은 두 품목을 반대 순서로 잠그면 서로를 기다리다 교착 상태(deadlock)가 된다.
	 * 할당도 같은 순서로 잠근다. 수주 확정과 입고가 같은 두 품목을 건드려도 순서가 같으니 서로 막히지 않는다.
	 */
	private static final Comparator<StockMovement> LOCK_ORDER = Comparator
			.comparing((StockMovement m) -> m.item().getId())
			.thenComparing(m -> m.warehouse().getId());
	private static final Comparator<StockAllocation> ALLOCATION_ORDER = Comparator
			.comparing((StockAllocation a) -> a.item().getId())
			.thenComparing(a -> a.warehouse().getId());

	private final StockRepository stockRepository;
	private final StockTransactionRepository transactionRepository;

	public StockLedger(StockRepository stockRepository, StockTransactionRepository transactionRepository) {
		this.stockRepository = stockRepository;
		this.transactionRepository = transactionRepository;
	}

	/** @return 기록한 원장 줄 (넘긴 순서가 아니라 잠근 순서) */
	public List<StockTransaction> post(List<StockMovement> movements) {
		String actor = CurrentUser.id();
		List<StockTransaction> posted = new ArrayList<>();
		for (StockMovement movement : movements.stream().sorted(LOCK_ORDER).toList()) {
			Long itemId = movement.item().getId();
			Long warehouseId = movement.warehouse().getId();
			stockRepository.insertIfAbsent(itemId, warehouseId, actor);
			Stock stock = stockRepository.findForUpdate(itemId, warehouseId).orElseThrow();

			BigDecimal expected = movement.expectedBalance();
			if (expected != null && stock.getQuantity().compareTo(expected) != 0) {
				throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION,
						"화면을 연 뒤 %s의 재고가 바뀌었습니다. (화면 %s, 지금 %s) 다시 조회한 뒤 실사 수량을 확인하세요."
								.formatted(movement.item().getCode(), expected.stripTrailingZeros().toPlainString(),
										stock.getQuantity().stripTrailingZeros().toPlainString()));
			}
			BigDecimal balance = stock.apply(movement.quantity());
			if (movement.type().consumesAllocation()) {
				// 출고는 할당해 둔 몫을 내보낸다. 현재고와 할당량이 같이 줄어서 가용 재고는 그대로다
				stock.allocate(movement.quantity());
			}
			posted.add(transactionRepository.save(new StockTransaction(movement, balance, actor)));
		}
		return posted;
	}

	/**
	 * 할당을 늘리거나 푼다. 늘릴 때는 잠근 뒤 가용 재고(현재고 - 할당량)가 충분한지 확인한다.
	 *
	 * 확인과 변경이 같은 잠금 안에 있어야 한다. 잠그지 않고 "가용 80 확인 → 80 할당"을 하면, 두 수주가 동시에 가용 80을 보고
	 * 둘 다 80씩 할당해 160을 약속하게 된다 (2단계 입고의 남은 수량, 3단계 현재고와 같은 check-then-act 문제).
	 * 부족한 줄이 여럿이면 처음 하나에서 멈추지 않고 모두 모아서 한 번에 알린다. 담당자가 한 번에 고칠 수 있게.
	 */
	public void allocate(List<StockAllocation> allocations) {
		List<String> shortages = new ArrayList<>();
		for (StockAllocation allocation : allocations.stream().sorted(ALLOCATION_ORDER).toList()) {
			Long itemId = allocation.item().getId();
			Long warehouseId = allocation.warehouse().getId();
			Stock stock = stockRepository.findForUpdate(itemId, warehouseId).orElse(null);
			if (allocation.quantity().signum() > 0) {
				BigDecimal available = stock == null ? BigDecimal.ZERO : stock.getAvailableQuantity();
				if (available.compareTo(allocation.quantity()) < 0) {
					shortages.add("%s(%s) 가용 %s, 요청 %s".formatted(allocation.item().getCode(),
							allocation.item().getName(), plain(available), plain(allocation.quantity())));
					continue;
				}
			}
			if (stock == null) {
				throw new IllegalStateException("할당을 풀 현재고 줄이 없습니다: item=%d, warehouse=%d"
						.formatted(itemId, warehouseId));
			}
			stock.allocate(allocation.quantity());
		}
		if (!shortages.isEmpty()) {
			throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK,
					"%s의 가용 재고가 부족합니다. %s".formatted(allocations.get(0).warehouse().getName(),
							String.join(", ", shortages)));
		}
	}

	private static String plain(BigDecimal value) {
		return value.stripTrailingZeros().toPlainString();
	}
}
