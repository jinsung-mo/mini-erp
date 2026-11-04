import { useState } from 'react'
import { type ApiError, toApiError } from '../../api/client'

/**
 * 기준정보 입력 폼의 저장 처리: 진행 중 표시, 서버 오류를 입력 필드별로 나눠 보여 주기.
 *
 * fieldCodes는 특정 입력 필드에 대한 업무 에러 code다.
 * 예) { DUPLICATE_CODE: 'code' }면 409 DUPLICATE_CODE의 메시지를 코드 입력 아래에 보여 준다.
 * 검증의 기준은 서버이고, 화면은 서버가 돌려준 ProblemDetail(code, errors)을 그대로 보여 준다.
 */
export function useMasterForm<T>(onSaved: (saved: T, message: string) => void, fieldCodes: Record<string, string>) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)

  async function run(action: () => Promise<T>, message: string) {
    setBusy(true)
    setError(null)
    try {
      onSaved(await action(), message)
    } catch (e) {
      setError(toApiError(e))
    } finally {
      setBusy(false)
    }
  }

  function fieldError(field: string): string | undefined {
    if (!error) return undefined
    if (fieldCodes[error.code] === field) return error.message
    return error.fieldErrors.find((fieldError) => fieldError.field === field)?.message ?? undefined
  }

  // 특정 필드에 붙지 않는 오류(404, DUPLICATE_KEY, 서버 오류 등)는 폼 위에 보여 준다
  const generalError = error && error.fieldErrors.length === 0 && !(error.code in fieldCodes) ? error : null

  return { busy, run, fieldError, generalError }
}
