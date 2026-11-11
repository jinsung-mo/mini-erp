package com.minierp.inventory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface StockRepository extends JpaRepository<Stock, Long> {

	/**
	 * 품목 × 창고의 현재고 줄이 없으면 0으로 만든다. 이미 있으면 아무것도 하지 않는다.
	 *
	 * "조회해서 없으면 save()"로 하면 처음 들어오는 품목에 입고 두 건이 동시에 오면 둘 다 "없음"을 보고 둘 다 INSERT한다.
	 * 하나는 uk_stock 위반으로 실패하고, PostgreSQL은 오류가 난 트랜잭션을 더 쓸 수 없어서 입고 전체가 실패한다.
	 * ON CONFLICT DO NOTHING은 먼저 INSERT한 트랜잭션이 끝날 때까지 기다렸다가 조용히 넘어간다.
	 */
	@Modifying
	@Query(value = """
			insert into stock (item_id, warehouse_id, quantity, created_at, created_by, updated_at, updated_by)
			values (:itemId, :warehouseId, 0, now(), :actor, now(), :actor)
			on conflict (item_id, warehouse_id) do nothing
			""", nativeQuery = true)
	void insertIfAbsent(@Param("itemId") Long itemId, @Param("warehouseId") Long warehouseId,
			@Param("actor") String actor);

	/** 현재고 줄을 잠근다 (SELECT ... FOR UPDATE). 같은 품목 × 창고를 바꾸는 다른 트랜잭션은 커밋할 때까지 기다린다 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from Stock s where s.item.id = :itemId and s.warehouse.id = :warehouseId")
	Optional<Stock> findForUpdate(@Param("itemId") Long itemId, @Param("warehouseId") Long warehouseId);

	Optional<Stock> findByItemIdAndWarehouseId(Long itemId, Long warehouseId);

	boolean existsByWarehouseIdAndQuantityGreaterThan(Long warehouseId, BigDecimal quantity);

	/** 품목 코드·이름으로 검색. 0인 줄은 기본으로 숨긴다 (한 번 들어왔다가 다 나간 품목이 쌓이면 목록이 길어진다) */
	@Query("""
			select s from Stock s
			join fetch s.item i
			join fetch s.warehouse w
			where (lower(i.code) like :pattern or lower(i.name) like :pattern)
			  and (:warehouseId is null or w.id = :warehouseId)
			  and (:includeZero = true or s.quantity <> 0)
			order by i.code, w.code
			""")
	List<Stock> search(@Param("pattern") String pattern, @Param("warehouseId") @Nullable Long warehouseId,
			@Param("includeZero") boolean includeZero);
}
