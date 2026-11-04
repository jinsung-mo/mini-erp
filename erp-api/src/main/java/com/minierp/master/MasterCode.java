package com.minierp.master;

/**
 * 기준정보 코드 규칙. 품목·거래처·창고가 같은 규칙을 쓴다 (회사 표준 하나).
 *
 * 코드는 엑셀·바코드·다른 시스템이 이 데이터를 부르는 이름이다. 대소문자나 공백이 섞이면 같은 것을 다르게 부르게 되므로
 * 영문 대문자·숫자·하이픈만 허용한다. 등록 후 바꿀 수 없으므로, 바뀔 수 있는 의미(예: 거래처의 역할)는 코드에 넣지 않는다.
 */
public final class MasterCode {

	public static final String PATTERN = "[A-Z0-9][A-Z0-9-]{1,19}";

	public static final String MESSAGE = "영문 대문자, 숫자, 하이픈(-)으로 2~20자여야 합니다.";

	private MasterCode() {
	}
}
