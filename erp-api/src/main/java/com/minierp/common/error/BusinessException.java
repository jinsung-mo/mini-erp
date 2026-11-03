package com.minierp.common.error;

/**
 * 업무 규칙 위반을 알리는 예외. (예: 재고 부족, 승인 안 된 발주의 입고)
 *
 * Service에서 던지면 GlobalExceptionHandler가 ErrorCode의 HTTP 상태와 code로 응답을 만든다.
 * RuntimeException이라서 @Transactional 메서드 안에서 던지면 트랜잭션도 롤백된다.
 */
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	public BusinessException(ErrorCode errorCode) {
		this(errorCode, errorCode.getMessage());
	}

	public BusinessException(ErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

	public ErrorCode getErrorCode() {
		return errorCode;
	}
}
