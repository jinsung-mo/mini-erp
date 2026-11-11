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
	INACTIVE_MASTER(HttpStatus.CONFLICT, "사용 중지된 기준정보는 새 거래에 쓸 수 없습니다."),
	PARTNER_HAS_OPEN_ORDERS(HttpStatus.CONFLICT, "진행 중인 거래가 있는 거래처는 사용 중지할 수 없습니다."),

	// 구매
	NOT_A_SUPPLIER(HttpStatus.CONFLICT, "공급처 역할이 없는 거래처에는 발주할 수 없습니다."),
	DUPLICATE_LINE_ITEM(HttpStatus.BAD_REQUEST, "한 문서에 같은 품목을 두 줄로 넣을 수 없습니다."),
	INVALID_QUANTITY_FOR_UNIT(HttpStatus.BAD_REQUEST, "이 단위는 소수 수량을 쓸 수 없습니다."),
	INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "지금 상태에서는 할 수 없는 작업입니다."),
	SELF_APPROVAL_NOT_ALLOWED(HttpStatus.FORBIDDEN, "본인이 작성하거나 승인 요청한 문서는 승인할 수 없습니다."),
	EXCEEDS_REMAINING_QUANTITY(HttpStatus.CONFLICT, "남은 수량보다 많이 처리할 수 없습니다."),

	// 재고
	INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "재고가 부족합니다."),
	WAREHOUSE_HAS_STOCK(HttpStatus.CONFLICT, "재고가 남아 있는 창고는 사용 중지할 수 없습니다."),
	NO_STOCK_DIFFERENCE(HttpStatus.BAD_REQUEST, "실사 수량이 장부 수량과 같아서 조정할 것이 없습니다."),

	// 동시 수정: 화면이 본 데이터를 그사이 다른 사용자가 바꾼 경우 (낙관적 락)
	CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "다른 사용자가 먼저 변경했습니다. 다시 조회한 뒤 시도하세요."),
	// 동시 처리: 같은 데이터를 잠그려는 다른 작업과 겹쳐 DB가 이번 요청을 취소한 경우 (교착 상태 등)
	LOCK_CONFLICT(HttpStatus.CONFLICT, "다른 작업과 동시에 처리되어 이번 요청을 취소했습니다. 잠시 후 다시 시도하세요."),

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
