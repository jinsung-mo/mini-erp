package com.minierp.inventory;

import java.time.LocalDate;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockTransactionRepository extends JpaRepository<StockTransaction, Long> {

	@Query(value = "select nextval('stock_adjustment_no_seq')", nativeQuery = true)
	long nextAdjustmentNo();

	/** 수불부 조회. 최근 기록이 위로 온다. 페이징은 6단계에서 다룬다 */
	@Query("""
			select t from StockTransaction t
			join fetch t.item i
			join fetch t.warehouse w
			where (lower(t.sourceNo) like :pattern or lower(i.code) like :pattern or lower(i.name) like :pattern)
			  and (:itemId is null or i.id = :itemId)
			  and (:warehouseId is null or w.id = :warehouseId)
			  and (:type is null or t.type = :type)
			  and (cast(:fromDate as localdate) is null or t.txnDate >= :fromDate)
			  and (cast(:toDate as localdate) is null or t.txnDate <= :toDate)
			order by t.id desc
			""")
	List<StockTransaction> search(@Param("pattern") String pattern, @Param("itemId") @Nullable Long itemId,
			@Param("warehouseId") @Nullable Long warehouseId, @Param("type") @Nullable StockTransactionType type,
			@Param("fromDate") @Nullable LocalDate fromDate, @Param("toDate") @Nullable LocalDate toDate);
}
