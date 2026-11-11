# mini-erp

구매 → 재고 → 판매가 **하나의 데이터로 이어지는** 미니 ERP.
백엔드(Spring Boot + JPA)부터 프론트(React)까지 직접 만들면서
"ERP가 왜 필요하고, 어디가 어렵고, 왜 이렇게 설계하는지"를 기록하는 학습 프로젝트입니다.

## 왜 ERP인가

부서마다 따로 시스템(또는 엑셀)을 쓰면 같은 사실이 여러 번, 서로 다르게 기록됩니다.

| 부서별로 따로 쓸 때 | ERP로 통합했을 때 |
|---|---|
| 구매팀은 "입고 100개", 창고 엑셀은 "95개" | 입고 처리 한 번이 재고와 원장에 동시에 반영 |
| 영업이 재고를 전화로 확인하고 수주 | 수주 시점에 가용 재고를 시스템이 확인 |
| 월말에 부서별 숫자를 맞추는 마감 작업 | 거래가 발생한 순간 숫자가 이미 맞아 있음 |

ERP의 본질은 화면이 아니라 **"한 번 입력된 거래가 모든 모듈에 일관되게 반영된다"** 는 보장입니다.
그래서 이 프로젝트의 중심도 CRUD가 아니라 **트랜잭션, 재고 정합성, 상태 전이**입니다.

## 다루는 업무 흐름

```
[기준정보]  품목 · 거래처 · 창고
     │
[구매]  발주(PO) ──승인──▶ 입고 ──────────┐
                                          ▼
[재고]                         재고 수불(입·출고 이력) ─▶ 현재고
                                          ▲
[판매]  수주(SO) ──가용재고 확인──▶ 출고 ──┘
```

## 구조

```
mini-erp/
├─ erp-web/            WEB  React + TypeScript (Vite)      :5173  /api → WAS로 프록시
├─ erp-api/            WAS  Spring Boot + JPA              :8082
│  ├─ com.minierp
│  │  ├─ common/       공통: 에러 응답(ProblemDetail + code), 감사 컬럼(BaseEntity), 작업자(CurrentUser)
│  │  ├─ system/       3계층 연결 확인용 상태 API
│  │  ├─ master/       기준정보: item/ partner/ warehouse/
│  │  ├─ purchase/     구매: order/ (발주) receipt/ (입고)
│  │  └─ inventory/    재고: 수불 원장, 현재고, 실사 조정   (다음 단계에서 sales/)
│  └─ db/migration/    Flyway 마이그레이션 (V1__create_item.sql ... V4__create_inventory.sql)
├─ docker-compose.yml  DB   PostgreSQL 17                  :55433
└─ docs/               단계별 설계 이유와 트러블슈팅 기록
```

## 기술 스택과 선택 이유

| 영역 | 기술 | 이유 |
|---|---|---|
| Backend | Java 17, Spring Boot 4.1, Spring Data JPA (Hibernate 7) | 엔터프라이즈 표준. 도메인 객체에 상태 전이 규칙을 담기 좋음 |
| DB | PostgreSQL 17 | 트랜잭션·락(`SELECT ... FOR UPDATE`)·제약조건을 실제 DB로 다루기 위해 |
| Schema | Flyway | 테이블 변경 이력을 SQL 파일로 Git에 남기고, 모든 환경에 같은 순서로 적용 |
| Frontend | React 19 + TypeScript (Vite), React Router | ERP 화면은 그리드·폼 중심이라 타입 안정성이 중요 |
| Error | RFC 9457 ProblemDetail + 업무 `code` | HTTP 상태코드는 표준대로, 화면 분기는 `code`로 |
| Test | JUnit 5, MockMvc, Testcontainers | 실제 PostgreSQL로 제약조건과 동시 요청 경쟁을 재현하고 검증하기 위해 |

## 실행 방법

```bash
# 1) DB
docker compose up -d

# 2) API (http://127.0.0.1:8082)
cd erp-api && ./gradlew bootRun

# 3) 화면 (http://localhost:5173)
cd erp-web && npm install && npm run dev
```

테스트: `cd erp-api && ./gradlew test` (Docker가 켜져 있어야 한다: Testcontainers가 PostgreSQL을 띄운다)
· 화면 빌드/린트: `cd erp-web && npm run build && npm run lint`

## 로드맵

- [x] 0. 프로젝트 골격: 3계층 연결, 공통 에러 응답, 상태 화면 → [docs/00-skeleton.md](docs/00-skeleton.md)
- [x] 1. 기준정보: 마스터 데이터의 의미 → [docs/01-master-data.md](docs/01-master-data.md)
  - [x] ① 품목: Flyway, 감사 컬럼, 사용 중지, 중복 코드 동시 요청 재현
  - [x] ② 거래처·창고: 공급처·고객 역할, 사업자등록번호 정규화·검증번호, 마이그레이션 실패 롤백·자동 flush 재현
- [x] 2. 구매: 발주 → 승인 → 입고, 상태 전이 → [docs/02-purchase.md](docs/02-purchase.md)
  - [x] 발주: 머리·줄·상태 이력, 전이 규칙 한곳, 직무 분리(본인 승인 금지), 화면 버전으로 낙관적 락
  - [x] 입고: 부분 입고, 문서·받은 수량·상태를 한 트랜잭션으로, 동시 입고는 발주 행 잠금 (잠금 vs 버전 직접 비교)
- [x] 3. 재고: 수불 이력(원장) 기반 현재고, 입고 연동 → [docs/03-inventory.md](docs/03-inventory.md)
  - [x] 원장이 원본, 현재고는 같은 트랜잭션에서 함께 바뀌는 합계. 재고를 바꾸는 입구는 `StockLedger` 하나
  - [x] 입고 연동과 기존 입고 이관, 실사 조정(장부 수량 비교), 재고가 남은 창고 사용 중지 금지
  - [x] 다른 발주의 동시 입고: 현재고 lost update, 잠금 순서와 교착 상태, 첫 INSERT 경쟁 재현
- [ ] 4. 판매: 수주 → 출고, 가용 재고 검증
- [ ] 5. 트러블슈팅: 동시 출고 시 재고 음수 재현 → 락/낙관적 락으로 해결
- [ ] 6. 트러블슈팅: N+1, 대량 조회 페이징, 정합성 검증 배치
- [ ] 7. 문서화: 아키텍처 결정 기록(ADR)

각 단계의 설계 이유와 트러블슈팅 과정은 `docs/`에 남깁니다.
