package com.minierp.system;

import java.time.OffsetDateTime;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 화면(WEB) → API(WAS) → DB 세 계층이 실제로 이어졌는지 확인하는 엔드포인트.
 *
 * 업무 규칙이 없는 상태 확인용이라 Service 없이 바로 조회한다.
 * 업무 API는 Controller → Service(트랜잭션, 업무 규칙) → Repository 순서를 지킨다.
 */
@RestController
@RequestMapping("/api/system")
public class SystemController {

	private final JdbcTemplate jdbcTemplate;

	public SystemController(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@GetMapping("/status")
	public SystemStatus status() {
		return jdbcTemplate.queryForObject("""
				select current_database()             as db_name,
				       split_part(version(), ' on ', 1) as db_version,
				       now()                          as db_time
				""",
				(rs, rowNum) -> new SystemStatus(
						"erp-api",
						rs.getString("db_name"),
						rs.getString("db_version"),
						rs.getObject("db_time", OffsetDateTime.class)));
	}

	public record SystemStatus(String application, String databaseName, String databaseVersion,
			OffsetDateTime databaseTime) {
	}
}
