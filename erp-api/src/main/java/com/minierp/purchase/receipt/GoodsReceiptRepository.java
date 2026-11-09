package com.minierp.purchase.receipt;

import java.time.LocalDate;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, Long> {

	@Query(value = "select nextval('goods_receipt_no_seq')", nativeQuery = true)
	long nextReceiptNo();

	/** 입고 번호·발주 번호·공급처로 검색. 발주·공급처·창고는 목록에 보이므로 fetch join으로 함께 가져온다 */
	@Query("""
			select gr from GoodsReceipt gr
			join fetch gr.purchaseOrder po
			join fetch po.supplier s
			join fetch gr.warehouse w
			where (lower(gr.receiptNo) like :pattern or lower(po.orderNo) like :pattern
			       or lower(s.code) like :pattern or lower(s.name) like :pattern)
			  and (:purchaseOrderId is null or po.id = :purchaseOrderId)
			  and (cast(:fromDate as localdate) is null or gr.receiptDate >= :fromDate)
			  and (cast(:toDate as localdate) is null or gr.receiptDate <= :toDate)
			order by gr.id desc
			""")
	List<GoodsReceipt> search(@Param("pattern") String pattern,
			@Param("purchaseOrderId") @Nullable Long purchaseOrderId,
			@Param("fromDate") @Nullable LocalDate fromDate,
			@Param("toDate") @Nullable LocalDate toDate);
}
