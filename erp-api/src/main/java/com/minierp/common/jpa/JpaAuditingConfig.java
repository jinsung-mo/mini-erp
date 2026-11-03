package com.minierp.common.jpa;

import java.time.Instant;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import com.minierp.common.web.CurrentUser;

/**
 * 저장할 때 BaseEntity의 등록자·수정자·일시를 자동으로 채운다.
 *
 * {@code @EnableJpaAuditing}을 메인 클래스(ErpApiApplication)에 붙이지 않고 따로 둔 이유:
 * 메인 클래스에 붙이면 JPA가 없는 테스트 슬라이스(@WebMvcTest)까지 JPA 설정을 찾다가 실패한다.
 * 테스트 슬라이스는 별도 @Configuration 클래스를 스캔하지 않는다.
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

	/** 누가: 현재 요청의 작업자 */
	@Bean
	AuditorAware<String> auditorAware() {
		return () -> Optional.of(CurrentUser.id());
	}

	/** 언제: 서버 시간대와 상관없는 시점(Instant, UTC)으로 남긴다. DB 컬럼은 timestamptz */
	@Bean
	DateTimeProvider auditingDateTimeProvider() {
		return () -> Optional.of(Instant.now());
	}
}
