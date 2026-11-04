import { useEffect, useState } from 'react'
import { type ApiError, toApiError } from '../../api/client'
import type { MasterRecord } from '../../api/master'

/** 사용 여부 조회 조건. 화면 기본값은 "사용"이다 (사용 중지된 데이터는 찾을 일이 드물다) */
export type ActiveFilter = 'all' | 'active' | 'inactive'

/** 화면의 사용 여부 선택 → API 조건 (전체면 조건을 보내지 않는다) */
export function toActive(filter: ActiveFilter): boolean | undefined {
  return filter === 'all' ? undefined : filter === 'active'
}

/** 오른쪽 입력 폼의 상태: 닫힘 / 신규 등록 / 선택한 행 수정 */
export type Selection<T> = { mode: 'create' } | { mode: 'edit'; record: T } | null

/**
 * 기준정보 화면의 흐름: 조회 → 행 선택(수정) 또는 신규 등록 → 저장 → 같은 조건으로 다시 조회.
 * 품목·거래처·창고 화면이 모두 이 흐름이라 훅 하나로 묶었다. 화면마다 다른 것(컬럼, 입력 항목)은 각 화면에 남긴다.
 *
 * fetchList에는 컴포넌트 밖에서 만든 함수(예: itemApi.search)를 넘긴다. 렌더링할 때마다 새로 만드는
 * 화살표 함수를 넘기면 effect의 의존성이 매번 바뀌어 조회가 끝없이 반복된다.
 */
export function useMasterPage<T extends MasterRecord, S>(fetchList: (search: S) => Promise<T[]>, initialSearch: S) {
  // 실제 조회 조건. seq를 올리면 같은 조건으로 다시 조회한다 (저장 후 목록 갱신)
  const [request, setRequest] = useState({ search: initialSearch, seq: 0 })
  const [rows, setRows] = useState<T[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<ApiError | null>(null)
  const [selection, setSelection] = useState<Selection<T>>(null)
  const [notice, setNotice] = useState<string | null>(null)

  useEffect(() => {
    // 응답이 늦게 오는 사이 조건이 바뀌면, 늦게 도착한 옛 결과는 버린다
    let ignore = false
    fetchList(request.search).then(
      (result) => {
        if (ignore) return
        setRows(result)
        setError(null)
        setLoading(false)
      },
      (e: unknown) => {
        if (ignore) return
        setError(toApiError(e))
        setLoading(false)
      },
    )
    return () => {
      ignore = true
    }
  }, [fetchList, request])

  function search(next: S) {
    setLoading(true)
    setRequest((prev) => ({ search: next, seq: prev.seq + 1 }))
  }

  function select(next: Selection<T>) {
    setNotice(null)
    setSelection(next)
  }

  /** 저장에 성공하면 그 행을 계속 수정하는 상태로 두고, 목록을 다시 조회한다 */
  function saved(record: T, message: string) {
    setNotice(message)
    setSelection({ mode: 'edit', record })
    setLoading(true)
    setRequest((prev) => ({ ...prev, seq: prev.seq + 1 }))
  }

  const editing = selection?.mode === 'edit' ? selection.record : null

  return {
    rows,
    loading,
    error,
    search,
    selection,
    editing,
    select,
    notice,
    saved,
    /** 다른 행을 고르거나 저장으로 서버 값이 바뀌면 폼을 새로 그린다 (입력 상태 초기화) */
    formKey: editing ? `${editing.id}-${editing.updatedAt}` : 'new',
  }
}
