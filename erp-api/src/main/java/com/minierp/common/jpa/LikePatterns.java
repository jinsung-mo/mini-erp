package com.minierp.common.jpa;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/**
 * 화면의 검색어를 LIKE 패턴으로 바꾼다. 검색어는 앞뒤 공백을 무시하고, 대소문자 구분 없이 부분 일치로 찾는다.
 * 컬럼 쪽은 쿼리에서 lower()로 맞춘다.
 */
public final class LikePatterns {

	private LikePatterns() {
	}

	/** " Bolt " → "%bolt%". 검색어가 없으면 "%%"(전부) */
	public static String contains(@Nullable String keyword) {
		return "%" + (keyword == null ? "" : keyword.strip().toLowerCase(Locale.ROOT)) + "%";
	}
}
