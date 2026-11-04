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
	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

	// 기준정보
	DUPLICATE_CODE(HttpStatus.CONFLICT, "이미 사용 중인 코드입니다."),
	DUPLICATE_BUSINESS_NO(HttpStatus.CONFLICT, "이미 등록된 사업자등록번호입니다."),

	// DB 제약조건: 애플리케이션 검사를 통과했지만 DB가 막은 경우 (주로 동시 요청)
	DUPLICATE_KEY(HttpStatus.CONFLICT, "같은 데이터가 이미 저장되어 있습니다. 다른 사용자가 먼저 저장했을 수 있으니 확인 후 다시 시도하세요.");

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
