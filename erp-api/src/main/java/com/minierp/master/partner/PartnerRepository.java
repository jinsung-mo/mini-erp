package com.minierp.master.partner;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PartnerRepository extends JpaRepository<Partner, Long> {

	boolean existsByCode(String code);

	Optional<Partner> findByBusinessNo(String businessNo);

	/**
	 * 코드·거래처명(대소문자 무시) 또는 사업자등록번호로 검색한다. null인 조건은 걸지 않는다.
	 * 역할 조건: supplier=true면 공급처 역할이 있는 거래처 전부다 (공급처이면서 고객인 거래처도 포함).
	 */
	@Query("""
			select p from Partner p
			where (lower(p.code) like :pattern
			       or lower(p.name) like :pattern
			       or (:businessNoPattern is not null and p.businessNo like :businessNoPattern))
			  and (:supplier is null or p.supplier = :supplier)
			  and (:customer is null or p.customer = :customer)
			  and (:active is null or p.active = :active)
			order by p.code
			""")
	List<Partner> search(@Param("pattern") String pattern,
			@Param("businessNoPattern") @Nullable String businessNoPattern,
			@Param("supplier") @Nullable Boolean supplier,
			@Param("customer") @Nullable Boolean customer,
			@Param("active") @Nullable Boolean active);
}
