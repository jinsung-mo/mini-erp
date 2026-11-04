package com.minierp.master.warehouse;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

	boolean existsByCode(String code);

	/** 코드 또는 창고명으로 검색한다 (대소문자 무시). active가 null이면 사용 여부와 상관없이 전부 */
	@Query("""
			select w from Warehouse w
			where (lower(w.code) like :pattern or lower(w.name) like :pattern)
			  and (:active is null or w.active = :active)
			order by w.code
			""")
	List<Warehouse> search(@Param("pattern") String pattern, @Param("active") @Nullable Boolean active);
}
