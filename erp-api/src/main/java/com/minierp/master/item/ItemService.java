package com.minierp.master.item;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minierp.common.error.BusinessException;
import com.minierp.common.error.ErrorCode;
import com.minierp.common.jpa.LikePatterns;
import com.minierp.master.item.ItemDtos.CreateRequest;
import com.minierp.master.item.ItemDtos.Response;
import com.minierp.master.item.ItemDtos.UpdateRequest;

/**
 * 품목 업무 규칙과 트랜잭션 경계.
 *
 * 클래스 기본값은 읽기 전용 트랜잭션이고, 데이터를 바꾸는 메서드에만 @Transactional을 다시 붙인다.
 * 엔티티 → 응답 DTO 변환도 트랜잭션 안(Service)에서 끝낸다. OSIV를 껐으므로 Controller에서는 지연 로딩을 할 수 없다.
 */
@Service
@Transactional(readOnly = true)
public class ItemService {

	private final ItemRepository itemRepository;

	public ItemService(ItemRepository itemRepository) {
		this.itemRepository = itemRepository;
	}

	public List<Response> search(@Nullable String keyword, @Nullable Boolean active) {
		return itemRepository.search(LikePatterns.contains(keyword), active).stream().map(Response::from).toList();
	}

	public Response get(Long id) {
		return Response.from(find(id));
	}

	@Transactional
	public Response create(CreateRequest request) {
		// 1차 방어: 흔한 경우(이미 있는 코드)를 친절한 메시지로 알려준다.
		// 하지만 두 요청이 동시에 이 검사를 통과할 수 있다. 그때는 DB unique 제약이 막는다 (2차 방어, DUPLICATE_KEY)
		if (itemRepository.existsByCode(request.code())) {
			throw new BusinessException(ErrorCode.DUPLICATE_CODE, "이미 사용 중인 품목 코드입니다: " + request.code());
		}
		Item item = itemRepository.save(new Item(request.code(), request.name(), request.spec(), request.unit()));
		return Response.from(item);
	}

	@Transactional
	public Response update(Long id, UpdateRequest request) {
		Item item = find(id);
		item.changeInfo(request.name(), request.spec(), request.unit());
		return flushAndRespond(item);
	}

	@Transactional
	public Response deactivate(Long id) {
		Item item = find(id);
		item.deactivate();
		return flushAndRespond(item);
	}

	@Transactional
	public Response activate(Long id) {
		Item item = find(id);
		item.activate();
		return flushAndRespond(item);
	}

	/**
	 * 변경 감지(dirty checking)는 보통 커밋 직전 flush 때 UPDATE를 실행하고, 이때 수정자·수정일시가 채워진다.
	 * flush 없이 바로 DTO를 만들면 응답에 "바뀌기 전" 수정자·수정일시가 담긴다. 그래서 먼저 flush한다.
	 */
	private Response flushAndRespond(Item item) {
		itemRepository.flush();
		return Response.from(item);
	}

	private Item find(Long id) {
		return itemRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "품목을 찾을 수 없습니다. (id=" + id + ")"));
	}
}
