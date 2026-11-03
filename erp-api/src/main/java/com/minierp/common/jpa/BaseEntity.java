package com.minierp.common.jpa;

import java.time.Instant;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;

/**
 * 모든 업무 테이블이 가지는 감사(audit) 컬럼.
 *
 * "누가 언제 이 데이터를 만들고 마지막으로 바꿨나"를 개발자가 직접 넣지 않고,
 * 저장(INSERT)·변경(UPDATE) 직전에 AuditingEntityListener가 채운다. 그래서 빠뜨릴 수 없다.
 *
 * 주의: UPDATE 시각은 커밋 직전 flush 때 채워진다. 같은 트랜잭션 안에서 바로 응답을 만들려면 먼저 flush해야 한다.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

	@CreatedDate
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@CreatedBy
	@Column(nullable = false, updatable = false, length = 50)
	private String createdBy;

	@LastModifiedDate
	@Column(nullable = false)
	private Instant updatedAt;

	@LastModifiedBy
	@Column(nullable = false, length = 50)
	private String updatedBy;

	public Instant getCreatedAt() {
		return createdAt;
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public String getUpdatedBy() {
		return updatedBy;
	}
}
