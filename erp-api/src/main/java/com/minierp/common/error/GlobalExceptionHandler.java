package com.minierp.common.error;

import java.sql.SQLException;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 모든 에러 응답을 RFC 9457 ProblemDetail 형식으로 통일한다.
 *
 * <pre>
 * HTTP/1.1 409 Conflict
 * { "type": "about:blank", "title": "Conflict", "status": 409,
 *   "detail": "재고가 부족합니다.", "instance": "/api/...", "code": "INSUFFICIENT_STOCK" }
 * </pre>
 *
 * ResponseEntityExceptionHandler를 상속하면 Spring이 던지는 예외(없는 URL 404, 405, JSON 파싱 실패 400 등)도
 * 같은 형식으로 나간다. 우리는 그 위에 code 필드만 덧붙인다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	/** PostgreSQL unique_violation */
	private static final String UNIQUE_VIOLATION = "23505";

	/** 1) 업무 규칙 위반: 의도한 실패라서 스택트레이스 없이 INFO로 남긴다 */
	@ExceptionHandler(BusinessException.class)
	public ProblemDetail handleBusiness(BusinessException e) {
		log.info("business error: {} - {}", e.getErrorCode(), e.getMessage());
		return problem(e.getErrorCode(), e.getMessage());
	}

	/** 2) @Valid 검증 실패: 어느 필드가 왜 틀렸는지 errors 배열로 알려준다 */
	@Override
	protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail body = problem(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.getMessage());
		body.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
				.map(error -> new FieldErrorDetail(error.getField(), error.getDefaultMessage()))
				.toList());
		return handleExceptionInternal(ex, body, headers, status, request);
	}

	/**
	 * DB 제약조건 위반.
	 * unique 위반(SQLState 23505)은 "중복 검사는 통과했지만 동시에 들어온 다른 요청이 먼저 저장한" 경우라서 409로 알려준다.
	 * 그 밖의 위반(NOT NULL, FK, CHECK)은 입력 검증이나 업무 규칙이 놓친 버그이므로 500으로 남긴다.
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ProblemDetail handleDataIntegrity(DataIntegrityViolationException e) {
		if (e.getMostSpecificCause() instanceof SQLException sql && UNIQUE_VIOLATION.equals(sql.getSQLState())) {
			log.warn("unique constraint violation: {}", sql.getMessage());
			return problem(ErrorCode.DUPLICATE_KEY, ErrorCode.DUPLICATE_KEY.getMessage());
		}
		return handleUnexpected(e);
	}

	/**
	 * 낙관적 락 실패: 버전을 확인하고 UPDATE하는 사이(update ... where version = ?)에 다른 트랜잭션이 먼저 커밋했다.
	 * 화면이 보낸 버전은 Service에서 먼저 비교하지만, 그 비교를 동시에 통과한 요청은 여기로 온다 (중복 코드와 같은 두 겹 방어).
	 */
	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	public ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException e) {
		log.info("optimistic lock failure: {}", e.getMessage());
		return problem(ErrorCode.CONCURRENT_MODIFICATION, ErrorCode.CONCURRENT_MODIFICATION.getMessage());
	}

	/**
	 * 비관적 락 실패: 교착 상태(SQLState 40P01)로 PostgreSQL이 한쪽 트랜잭션을 끊었거나, 잠금을 얻지 못했다.
	 * 데이터는 롤백돼서 맞지만 서버 버그는 아니므로 500 대신 409로 "다시 시도"를 알린다.
	 * 재고는 잠그는 순서를 정해서(StockLedger) 교착 상태가 나지 않게 하고, 이 처리는 그래도 났을 때의 안전망이다.
	 */
	@ExceptionHandler(PessimisticLockingFailureException.class)
	public ProblemDetail handlePessimisticLock(PessimisticLockingFailureException e) {
		log.warn("pessimistic lock failure: {}", e.getMostSpecificCause().getMessage());
		return problem(ErrorCode.LOCK_CONFLICT, ErrorCode.LOCK_CONFLICT.getMessage());
	}

	/** 3) 예상하지 못한 예외: 원인은 로그에만 남기고, 응답에는 내부 정보를 노출하지 않는다 */
	@ExceptionHandler(Exception.class)
	public ProblemDetail handleUnexpected(Exception e) {
		log.error("unexpected error", e);
		return problem(ErrorCode.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_SERVER_ERROR.getMessage());
	}

	/** Spring이 만든 ProblemDetail에도 code가 빠지지 않게 HTTP 상태 이름을 code로 넣는다 (예: METHOD_NOT_ALLOWED) */
	@Override
	protected ResponseEntity<Object> createResponseEntity(@Nullable Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		if (body instanceof ProblemDetail problem && !hasCode(problem)) {
			HttpStatus status = HttpStatus.resolve(statusCode.value());
			problem.setProperty("code", status != null ? status.name() : "HTTP_" + statusCode.value());
		}
		return super.createResponseEntity(body, headers, statusCode, request);
	}

	private static ProblemDetail problem(ErrorCode code, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.getStatus(), detail);
		problem.setProperty("code", code.name());
		return problem;
	}

	private static boolean hasCode(ProblemDetail problem) {
		Map<String, @Nullable Object> properties = problem.getProperties();
		return properties != null && properties.containsKey("code");
	}

	public record FieldErrorDetail(String field, @Nullable String message) {
	}
}
