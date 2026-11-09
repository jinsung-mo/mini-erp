package com.minierp.master.partner;

import java.util.List;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minierp.common.error.BusinessException;
import com.minierp.common.error.ErrorCode;
import com.minierp.common.jpa.LikePatterns;
import com.minierp.master.partner.PartnerDtos.CreateRequest;
import com.minierp.master.partner.PartnerDtos.Response;
import com.minierp.master.partner.PartnerDtos.UpdateRequest;

/**
 * 거래처 업무 규칙과 트랜잭션 경계. 품목과 같은 뼈대에 "한 회사는 한 번만 등록" 규칙이 더해진다.
 */
@Service
@Transactional(readOnly = true)
public class PartnerService {

	/** 숫자·하이픈·공백으로만 된 검색어(예: "123-45")는 사업자등록번호로도 찾는다 */
	private static final Pattern BUSINESS_NO_KEYWORD = Pattern.compile("[0-9][0-9\\s-]*");

	private final PartnerRepository partnerRepository;

	/** 사용 중지해도 되는지 묻는 다른 모듈들 (구매: 진행 중인 발주). 기준정보는 그 모듈들을 모른다 */
	private final List<PartnerDeactivationGuard> deactivationGuards;

	public PartnerService(PartnerRepository partnerRepository, List<PartnerDeactivationGuard> deactivationGuards) {
		this.partnerRepository = partnerRepository;
		this.deactivationGuards = deactivationGuards;
	}

	public List<Response> search(@Nullable String keyword, @Nullable PartnerRole role, @Nullable Boolean active) {
		Boolean supplier = role == PartnerRole.SUPPLIER ? Boolean.TRUE : null;
		Boolean customer = role == PartnerRole.CUSTOMER ? Boolean.TRUE : null;
		return partnerRepository
				.search(LikePatterns.contains(keyword), businessNoPattern(keyword), supplier, customer, active)
				.stream().map(Response::from).toList();
	}

	public Response get(Long id) {
		return Response.from(find(id));
	}

	@Transactional
	public Response create(CreateRequest request) {
		if (partnerRepository.existsByCode(request.code())) {
			throw new BusinessException(ErrorCode.DUPLICATE_CODE, "이미 사용 중인 거래처 코드입니다: " + request.code());
		}
		String businessNo = BusinessNumber.normalize(request.businessNo());
		checkBusinessNoNotTaken(businessNo, null);
		Partner partner = partnerRepository.save(new Partner(request.code(), request.name(), businessNo, request.roles()));
		return Response.from(partner);
	}

	@Transactional
	public Response update(Long id, UpdateRequest request) {
		Partner partner = find(id);
		String businessNo = BusinessNumber.normalize(request.businessNo());
		// 엔티티를 바꾸기 전에 검사한다. 먼저 바꾸면 검사 쿼리 직전의 자동 flush가 UPDATE를 먼저 실행해서
		// 친절한 DUPLICATE_BUSINESS_NO 대신 DB 제약 위반(DUPLICATE_KEY)이 난다
		checkBusinessNoNotTaken(businessNo, partner.getId());
		partner.changeInfo(request.name(), businessNo, request.roles());
		return flushAndRespond(partner);
	}

	@Transactional
	public Response deactivate(Long id) {
		Partner partner = find(id);
		deactivationGuards.forEach(guard -> guard.checkDeactivatable(partner));
		partner.deactivate();
		return flushAndRespond(partner);
	}

	@Transactional
	public Response activate(Long id) {
		Partner partner = find(id);
		partner.activate();
		return flushAndRespond(partner);
	}

	/**
	 * 한 회사는 거래처 하나로만 등록한다. 이미 있으면 어느 거래처인지 알려 준다.
	 * 대개 "새로 등록하지 말고 그 거래처에 역할을 추가하라"는 뜻이다.
	 *
	 * 수정할 때는 자기 자신을 빼고 찾는다. 빼지 않으면 이름만 고쳐도 자기 사업자등록번호와 "중복"이 된다.
	 * 품목 코드처럼 동시 요청은 이 검사를 함께 통과할 수 있고, 그때는 uk_partner_business_no 제약이 막는다 (DUPLICATE_KEY).
	 */
	private void checkBusinessNoNotTaken(@Nullable String businessNo, @Nullable Long selfId) {
		if (businessNo == null) {
			return;
		}
		partnerRepository.findByBusinessNo(businessNo)
				.filter(other -> !other.getId().equals(selfId))
				.ifPresent(other -> {
					throw new BusinessException(ErrorCode.DUPLICATE_BUSINESS_NO, "이미 등록된 사업자등록번호입니다: %s (%s %s)"
							.formatted(BusinessNumber.format(businessNo), other.getCode(), other.getName()));
				});
	}

	/**
	 * 검색어가 사업자등록번호처럼 생겼을 때만 사업자등록번호 조건을 만든다.
	 * 검색어에서 숫자만 뽑아 쓰면 안 된다. "볼트"처럼 숫자가 없는 검색어는 빈 문자열이 되고, "%%"는 모든 거래처와 일치한다.
	 */
	private static @Nullable String businessNoPattern(@Nullable String keyword) {
		if (keyword == null || !BUSINESS_NO_KEYWORD.matcher(keyword.strip()).matches()) {
			return null;
		}
		return "%" + BusinessNumber.normalize(keyword) + "%";
	}

	/** 수정자·수정일시는 flush(UPDATE 실행) 때 채워지므로, 응답을 만들기 전에 flush한다 (ItemService와 같은 이유) */
	private Response flushAndRespond(Partner partner) {
		partnerRepository.flush();
		return Response.from(partner);
	}

	private Partner find(Long id) {
		return partnerRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "거래처를 찾을 수 없습니다. (id=" + id + ")"));
	}
}
