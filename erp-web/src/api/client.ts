import { getOperatorId } from '../session/operator'

/** 백엔드 에러 응답 형식: RFC 9457 ProblemDetail + 업무 에러 code */
export type ProblemDetail = {
  type?: string
  title?: string
  status: number
  detail?: string
  instance?: string
  code: string
  errors?: FieldError[]
}

export type FieldError = { field: string; message: string | null }

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: FieldError[]
  /** API 서버가 직접 응답했는지. false면 요청이 API 서버까지 가지 못한 것 */
  readonly fromServer: boolean

  constructor(status: number, code: string, message: string, fieldErrors: FieldError[], fromServer: boolean) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
    this.fromServer = fromServer
  }
}

/**
 * 모든 API 호출이 지나가는 한 곳.
 * 성공하면 JSON을 돌려주고, 실패하면 항상 ApiError를 던진다. 화면은 error.code로 분기한다.
 */
export async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers)
  if (init?.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  // 서버가 등록자·수정자로 남길 작업자
  headers.set('X-User', getOperatorId())

  let res: Response
  try {
    res = await fetch(`/api${path}`, { ...init, headers })
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', '네트워크 오류로 요청을 보내지 못했습니다.', [], false)
  }

  if (!res.ok) {
    const problem = await readProblem(res)
    if (problem) {
      const message = problem.detail ?? problem.title ?? '요청을 처리하지 못했습니다.'
      throw new ApiError(problem.status, problem.code, message, problem.errors ?? [], true)
    }
    // ProblemDetail이 아니면 API 서버가 아니라 중간(Vite 프록시 등)에서 실패한 것이다
    throw new ApiError(res.status, 'API_UNREACHABLE', 'API 서버에 연결할 수 없습니다.', [], false)
  }

  if (res.status === 204) {
    return undefined as T
  }
  return (await res.json()) as T
}

/** 조회 조건 → 쿼리 문자열 ("?keyword=볼트&active=true"). 값이 없는 조건(undefined, 빈 문자열)은 보내지 않는다 */
export function queryString(params: Record<string, string | boolean | undefined>): string {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== '') query.set(key, String(value))
  }
  const qs = query.toString()
  return qs ? `?${qs}` : ''
}

/** catch로 받은 값을 ApiError로 맞춘다 (api()는 항상 ApiError를 던지지만, 그 밖의 코드에서 난 예외도 대비) */
export function toApiError(e: unknown): ApiError {
  return e instanceof ApiError ? e : new ApiError(0, 'UNKNOWN', '알 수 없는 오류가 발생했습니다.', [], false)
}

async function readProblem(res: Response): Promise<ProblemDetail | null> {
  try {
    const body: unknown = await res.json()
    return isProblemDetail(body) ? body : null
  } catch {
    return null
  }
}

function isProblemDetail(body: unknown): body is ProblemDetail {
  return (
    typeof body === 'object' &&
    body !== null &&
    typeof (body as ProblemDetail).status === 'number' &&
    typeof (body as ProblemDetail).code === 'string'
  )
}
