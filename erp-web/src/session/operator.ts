/**
 * 작업자 (로그인 기능 대신 쓰는 임시 장치).
 *
 * 선택한 작업자 id는 모든 API 요청에 X-User 헤더로 실리고, 서버는 이 값을 등록자·수정자로 남긴다.
 * HTTP 헤더에는 ASCII만 안전하게 실을 수 있어서 한글 이름이 아니라 영문 id를 보낸다.
 */
export type Operator = { id: string; name: string }

export const OPERATORS: Operator[] = [
  { id: 'admin', name: '기준정보 관리자' },
  { id: 'buyer01', name: '구매 담당' },
  { id: 'wh01', name: '창고 담당' },
  { id: 'sales01', name: '영업 담당' },
]

const STORAGE_KEY = 'mini-erp.operator'

let current = load()

function load(): string {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved && OPERATORS.some((operator) => operator.id === saved)) {
      return saved
    }
  } catch {
    // 저장소를 쓸 수 없는 환경이면 기본 작업자로 시작한다
  }
  return OPERATORS[0].id
}

export function getOperatorId(): string {
  return current
}

export function setOperatorId(id: string): void {
  current = id
  try {
    localStorage.setItem(STORAGE_KEY, id)
  } catch {
    // 저장에 실패해도 이번 화면에서는 유지된다
  }
}
