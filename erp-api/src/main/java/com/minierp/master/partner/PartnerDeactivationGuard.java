package com.minierp.master.partner;

/**
 * 거래처를 사용 중지해도 되는지 다른 모듈에 묻는다. 안 되면 BusinessException을 던진다.
 *
 * 구매 모듈은 기준정보(거래처)를 참조하지만, 기준정보는 구매를 모른다. "진행 중인 발주가 있으면 사용 중지 불가" 규칙을
 * PartnerService가 발주 Repository를 직접 불러서 구현하면 master → purchase 방향의 의존이 생겨 둘이 서로를 참조하게 된다.
 * 그래서 기준정보 쪽은 이 인터페이스만 정해 두고, 구현은 구매 모듈이 한다 (의존성 역전). 4단계 판매도 같은 방식으로 붙는다.
 */
public interface PartnerDeactivationGuard {

	void checkDeactivatable(Partner partner);
}
