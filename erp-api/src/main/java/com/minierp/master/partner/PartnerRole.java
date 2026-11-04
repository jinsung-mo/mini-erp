package com.minierp.master.partner;

/**
 * 거래처의 역할. 한 거래처가 둘 다 가질 수 있다 (예: 원자재를 사 오면서 우리 제품도 사 가는 회사).
 * 2단계 발주는 SUPPLIER, 4단계 수주는 CUSTOMER 역할이 있는 거래처만 고를 수 있다.
 */
public enum PartnerRole {

	/** 공급처: 우리가 물건을 사 오는 곳 (매입) */
	SUPPLIER,

	/** 고객: 우리가 물건을 파는 곳 (매출) */
	CUSTOMER
}
