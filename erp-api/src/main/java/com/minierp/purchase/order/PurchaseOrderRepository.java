package com.minierp.purchase.order;

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

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

	/** 발주 번호용 시퀀스. 번호는 "PO-" + 6자리로 만든다 */
	@Query(value = "select nextval('purchase_order_no_seq')", nativeQuery = true)
	long nextOrderNo();

	/**
	 * 목록 조회. 거래처·창고는 fetch join으로 한 번에 가져온다 (목록에 이름을 보여 주므로).
	 * 줄은 금액 합계 계산 때 지연 로딩되며, default_batch_fetch_size 설정으로 발주 N건의 줄을 IN 쿼리 한 번에 가져온다.
	 */
	@Query("""
			select po from PurchaseOrder po
			join fetch po.supplier s
			join fetch po.warehouse w
			where (lower(po.orderNo) like :pattern or lower(s.code) like :pattern or lower(s.name) like :pattern)
			  and po.status in :statuses
			  and (:supplierId is null or s.id = :supplierId)
			  and (cast(:fromDate as localdate) is null or po.orderDate >= :fromDate)
			  and (cast(:toDate as localdate) is null or po.orderDate <= :toDate)
			order by po.id desc
			""")
	List<PurchaseOrder> search(@Param("pattern") String pattern,
			@Param("statuses") Collection<PurchaseOrderStatus> statuses,
			@Param("supplierId") @Nullable Long supplierId,
			@Param("fromDate") @Nullable LocalDate fromDate,
			@Param("toDate") @Nullable LocalDate toDate);

	/**
	 * 입고용 조회: 행을 잠그고(SELECT ... FOR UPDATE) 버전도 올린다.
	 *
	 * 같은 발주에 입고 두 건이 동시에 들어오면 둘 다 "남은 수량 10"을 보고 10개씩 받을 수 있다. 잠그면 두 번째 요청은
	 * 첫 번째가 커밋할 때까지 기다렸다가 "남은 수량 0"을 본다. 버전을 올리는 이유는 입고가 상태를 바꾸지 않는 경우
	 * (부분 입고 → 부분 입고)에도, 그 발주를 열어 둔 다른 화면의 마감·취소가 옛 화면 기준으로 실행되지 않게 하려는 것이다.
	 */
	@Lock(LockModeType.PESSIMISTIC_FORCE_INCREMENT)
	@Query("select po from PurchaseOrder po where po.id = :id")
	Optional<PurchaseOrder> findByIdForReceipt(@Param("id") Long id);

	long countBySupplierIdAndStatusIn(Long supplierId, Collection<PurchaseOrderStatus> statuses);
}
