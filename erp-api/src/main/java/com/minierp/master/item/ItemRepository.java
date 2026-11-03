package com.minierp.master.item;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemRepository extends JpaRepository<Item, Long> {

	boolean existsByCode(String code);

	/**
	 * 코드 또는 품목명으로 검색한다. active가 null이면 사용 여부와 상관없이 전부.
	 * 지금은 전체 목록을 한 번에 돌려준다. 품목이 수만 건이 되면 느려지는데, 6단계에서 페이징으로 바꾼다.
	 */
	@Query("""
			select i from Item i
			where (lower(i.code) like :pattern or lower(i.name) like :pattern)
			  and (:active is null or i.active = :active)
			order by i.code
			""")
	List<Item> search(@Param("pattern") String pattern, @Param("active") @Nullable Boolean active);
}
