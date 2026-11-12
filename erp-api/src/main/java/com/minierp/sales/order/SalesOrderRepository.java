package com.minierp.sales.order;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

	/** 수주 번호용 시퀀스. 번호는 "SO-" + 6자리로 만든다 */
	@Query(value = "select nextval('sales_order_no_seq')", nativeQuery = true)
	long nextOrderNo();

	/**
	 * 목록 조회. 거래처·창고는 fetch join으로 한 번에 가져온다 (목록에 이름을 보여 주므로).
	 * 줄은 금액 합계 계산 때 지연 로딩되며, default_batch_fetch_size 설정으로 수주 N건의 줄을 IN 쿼리 한 번에 가져온다.
	 */
	@Query("""
			select so from SalesOrder so
			join fetch so.customer s
			join fetch so.warehouse w
			where (lower(so.orderNo) like :pattern or lower(s.code) like :pattern or lower(s.name) like :pattern)
			  and so.status in :statuses
			  and (:customerId is null or s.id = :customerId)
			  and (cast(:fromDate as localdate) is null or so.orderDate >= :fromDate)
			  and (cast(:toDate as localdate) is null or so.orderDate <= :toDate)
			order by so.id desc
			""")
	List<SalesOrder> search(@Param("pattern") String pattern,
			@Param("statuses") Collection<SalesOrderStatus> statuses,
			@Param("customerId") @Nullable Long customerId,
			@Param("fromDate") @Nullable LocalDate fromDate,
			@Param("toDate") @Nullable LocalDate toDate);

	/**
	 * 재고를 건드리는 작업(확정, 출고, 취소, 마감)용 조회: 행을 잠그고(SELECT ... FOR UPDATE) 버전도 올린다.
	 *
	 * 같은 수주의 확정이 두 번 동시에 들어오면 둘 다 "작성 중"을 보고 둘 다 할당할 수 있다. 잠그면 두 번째 요청은 첫 번째가
	 * 커밋할 때까지 기다렸다가 "확정"을 보고 전이 규칙에 막힌다. 출고도 입고처럼 남은 수량을 두 번 내보내지 않게 막는다.
	 * 잠그는 순서는 항상 수주 → 현재고다 (입고도 발주 → 현재고). 거꾸로 잠그는 길이 없어서 교착 상태가 생기지 않는다.
	 */
	@Lock(LockModeType.PESSIMISTIC_FORCE_INCREMENT)
	@Query("select so from SalesOrder so where so.id = :id")
	Optional<SalesOrder> findByIdForUpdate(@Param("id") Long id);

	long countByCustomerIdAndStatusIn(Long customerId, Collection<SalesOrderStatus> statuses);
}
