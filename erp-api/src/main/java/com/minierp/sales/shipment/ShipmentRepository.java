package com.minierp.sales.shipment;

import java.time.LocalDate;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

	@Query(value = "select nextval('shipment_no_seq')", nativeQuery = true)
	long nextShipmentNo();

	/** 출고 번호·수주 번호·고객으로 검색. 수주·고객·창고는 목록에 보이므로 fetch join으로 함께 가져온다 */
	@Query("""
			select sh from Shipment sh
			join fetch sh.salesOrder so
			join fetch so.customer c
			join fetch sh.warehouse w
			where (lower(sh.shipmentNo) like :pattern or lower(so.orderNo) like :pattern
			       or lower(c.code) like :pattern or lower(c.name) like :pattern)
			  and (:salesOrderId is null or so.id = :salesOrderId)
			  and (cast(:fromDate as localdate) is null or sh.shipDate >= :fromDate)
			  and (cast(:toDate as localdate) is null or sh.shipDate <= :toDate)
			order by sh.id desc
			""")
	List<Shipment> search(@Param("pattern") String pattern,
			@Param("salesOrderId") @Nullable Long salesOrderId,
			@Param("fromDate") @Nullable LocalDate fromDate,
			@Param("toDate") @Nullable LocalDate toDate);
}
