package com.minierp.common.error;

import org.springframework.http.HttpStatus;

/**
 * 업무 에러 코드.
 *
 * 화면은 메시지 문구가 아니라 code로 분기한다. 문구는 바뀔 수 있지만 code는 API 계약이다.
 * 모듈이 생길 때마다 여기에 코드를 추가한다. (예: INSUFFICIENT_STOCK, INVALID_STATUS_TRANSITION)
 */
public enum ErrorCode {

	// 공통
	INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
	NOT_FOUND(HttpStatus.NOT_FOUND, "대상을 찾을 수 없습니다."),
	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

	private final HttpStatus status;
	private final String message;

	ErrorCode(HttpStatus status, String message) {
		this.status = status;
		this.message = message;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getMessage() {
		return message;
	}
}
