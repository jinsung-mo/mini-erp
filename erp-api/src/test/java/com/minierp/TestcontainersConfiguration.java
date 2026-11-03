package com.minierp;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 테스트용 PostgreSQL. 운영(docker-compose)과 같은 버전을 쓴다.
 *
 * {@code @ServiceConnection}: 컨테이너가 뜬 뒤 접속 주소·계정을 spring.datasource에 자동으로 넣어준다.
 * 같은 설정을 쓰는 테스트 클래스끼리는 Spring 테스트 컨텍스트가 캐시되므로 컨테이너도 한 번만 뜬다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
	}
}
