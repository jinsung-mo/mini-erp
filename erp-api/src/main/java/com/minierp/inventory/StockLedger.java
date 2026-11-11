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
 * 재고를 바꾸는 유일한 입구. 원장 한 줄을 쌓고 현재고를 같은 만큼 바꾼다.
 *
 * Propagation.MANDATORY: 반드시 부르는 쪽의 트랜잭션 안에서만 돈다. 입고 문서는 저장됐는데 재고는 안 바뀐 상태
 * (또는 그 반대)가 생기지 않도록, 원장 기록이 혼자 따로 커밋되는 길을 막는다. 트랜잭션 밖에서 부르면 바로 예외가 난다.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class StockLedger {

	/** 잠그는 순서. 두 입고가 같은 두 품목을 반대 순서로 잠그면 서로를 기다리다 교착 상태(deadlock)가 된다 */
	private static final Comparator<StockMovement> LOCK_ORDER = Comparator
			.comparing((StockMovement m) -> m.item().getId())
			.thenComparing(m -> m.warehouse().getId());

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
			posted.add(transactionRepository.save(new StockTransaction(movement, balance, actor)));
		}
		return posted;
	}
}
