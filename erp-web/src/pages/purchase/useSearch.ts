import { useEffect, useState } from 'react'
import { type ApiError, toApiError } from '../../api/client'

/**
 * 조회 조건 → 목록. reload()는 같은 조건으로 다시 조회한다 (저장·입고 후 목록 갱신).
 * 기준정보의 useMasterPage와 같은 흐름에서 "행 선택·입력 폼"만 뺀 것이다. 구매 문서는 목록과 문서 화면이 따로 있다.
 *
 * fetchList는 컴포넌트 밖에서 만든 함수(예: purchaseOrderApi.search)를 넘긴다 (렌더링마다 새 함수면 조회가 끝없이 반복된다).
 */
export function useSearch<T, S>(fetchList: (search: S) => Promise<T[]>, initialSearch: S) {
  const [request, setRequest] = useState({ search: initialSearch, seq: 0 })
  const [rows, setRows] = useState<T[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<ApiError | null>(null)

  useEffect(() => {
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

  function reload() {
    setLoading(true)
    setRequest((prev) => ({ ...prev, seq: prev.seq + 1 }))
  }

  return { rows, loading, error, search, reload }
}
