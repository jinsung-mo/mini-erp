package com.minierp.common.web;

import java.util.regex.Pattern;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 현재 요청을 보낸 작업자.
 *
 * 로그인 기능 대신 화면이 보내는 X-User 헤더를 쓴다.
 * 헤더는 누구나 바꿔 보낼 수 있으므로 학습용이다. 실서비스에서는 인증 토큰(Spring Security)에서 꺼낸다.
 */
public final class CurrentUser {

	public static final String HEADER = "X-User";

	/** HTTP 요청 밖(배치, 테스트 코드 등)에서 데이터를 바꿀 때 */
	public static final String SYSTEM = "system";

	/** 헤더가 없거나 형식이 틀릴 때 */
	public static final String ANONYMOUS = "anonymous";

	// HTTP 헤더 값은 ASCII만 안전하다. 한글 이름 대신 영문 id를 보낸다
	private static final Pattern USER_ID = Pattern.compile("[a-z][a-z0-9_.-]{0,29}");

	private CurrentUser() {
	}

	public static String id() {
		if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
			return SYSTEM;
		}
		String header = attributes.getRequest().getHeader(HEADER);
		return header != null && USER_ID.matcher(header).matches() ? header : ANONYMOUS;
	}
}
