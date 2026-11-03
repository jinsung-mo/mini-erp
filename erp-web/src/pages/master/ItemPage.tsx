import { useEffect, useState } from 'react'
import { type ApiError, toApiError } from '../../api/client'
import { type Item, type ItemSearch, itemApi } from '../../api/items'
import { formatDateTime } from '../../format'
import { ItemForm } from './ItemForm'

type ActiveFilter = 'all' | 'active' | 'inactive'
type Filter = { keyword: string; active: ActiveFilter }
type Selection = { mode: 'create' } | { mode: 'edit'; item: Item } | null

const INITIAL_FILTER: Filter = { keyword: '', active: 'active' }

function toSearch(filter: Filter): ItemSearch {
  return {
    keyword: filter.keyword.trim() || undefined,
    active: filter.active === 'all' ? undefined : filter.active === 'active',
  }
}

/**
 * 품목 화면: 왼쪽 목록(조회) + 오른쪽 입력 폼(등록·수정). ERP 기준정보 화면의 전형적인 구성이다.
 */
export function ItemPage() {
  const [filter, setFilter] = useState<Filter>(INITIAL_FILTER)
  // 실제 조회 조건. seq를 올리면 같은 조건으로 다시 조회한다 (저장 후 목록 갱신)
  const [request, setRequest] = useState({ search: toSearch(INITIAL_FILTER), seq: 0 })
  const [items, setItems] = useState<Item[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<ApiError | null>(null)
  const [selection, setSelection] = useState<Selection>(null)
  const [notice, setNotice] = useState<string | null>(null)

  useEffect(() => {
    let ignore = false
    itemApi.search(request.search).then(
      (result) => {
        if (ignore) return
        setItems(result)
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
  }, [request])

  function search() {
    setLoading(true)
    setRequest((prev) => ({ search: toSearch(filter), seq: prev.seq + 1 }))
  }

  function select(next: Selection) {
    setNotice(null)
    setSelection(next)
  }

  function handleSaved(saved: Item, message: string) {
    setNotice(message)
    setSelection({ mode: 'edit', item: saved })
    setLoading(true)
    setRequest((prev) => ({ ...prev, seq: prev.seq + 1 }))
  }

  const selectedId = selection?.mode === 'edit' ? selection.item.id : null

  return (
    <section className="page page-wide">
      <div className="page-header">
        <div>
          <h1>품목</h1>
          <p className="page-desc">
            구매·재고·판매의 모든 거래가 참조하는 기준 정보입니다. 코드는 등록 후 바꿀 수 없고, 삭제 대신 사용
            중지합니다.
          </p>
        </div>
        <button type="button" className="btn" onClick={() => select({ mode: 'create' })}>
          신규 등록
        </button>
      </div>

      <form
        className="filter-bar"
        onSubmit={(event) => {
          event.preventDefault()
          search()
        }}
      >
        <label>
          검색어
          <input
            value={filter.keyword}
            onChange={(e) => setFilter({ ...filter, keyword: e.target.value })}
            placeholder="코드 또는 품목명"
          />
        </label>
        <label>
          사용 여부
          <select
            value={filter.active}
            onChange={(e) => setFilter({ ...filter, active: e.target.value as ActiveFilter })}
          >
            <option value="all">전체</option>
            <option value="active">사용</option>
            <option value="inactive">사용 중지</option>
          </select>
        </label>
        <button type="submit" className="btn btn-secondary" disabled={loading}>
          조회
        </button>
      </form>

      <div className={selection ? 'split' : undefined}>
        <div className="grid-panel">
          <div className="grid-meta">{loading ? '조회 중…' : `${items.length}건`}</div>
          {error ? (
            <div className="alert" role="alert">
              <div className="alert-title">
                <code>{error.code}</code> {error.message}
              </div>
            </div>
          ) : (
            <table className="grid">
              <thead>
                <tr>
                  <th>코드</th>
                  <th>품목명</th>
                  <th>규격</th>
                  <th>단위</th>
                  <th>상태</th>
                  <th>수정자</th>
                  <th>수정일시</th>
                </tr>
              </thead>
              <tbody>
                {items.map((item) => (
                  <tr
                    key={item.id}
                    className={[item.id === selectedId && 'row-selected', !item.active && 'row-inactive']
                      .filter(Boolean)
                      .join(' ')}
                    onClick={() => select({ mode: 'edit', item })}
                    onKeyDown={(e) => e.key === 'Enter' && select({ mode: 'edit', item })}
                    tabIndex={0}
                  >
                    <td>
                      <code>{item.code}</code>
                    </td>
                    <td>{item.name}</td>
                    <td>{item.spec ?? '-'}</td>
                    <td>{item.unit}</td>
                    <td>{item.active ? <span className="tag tag-on">사용</span> : <span className="tag tag-off">중지</span>}</td>
                    <td>{item.updatedBy}</td>
                    <td>{formatDateTime(item.updatedAt)}</td>
                  </tr>
                ))}
                {!loading && items.length === 0 && (
                  <tr>
                    <td colSpan={7} className="grid-empty">
                      조회된 품목이 없습니다.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          )}
        </div>

        {selection && (
          <ItemForm
            // 다른 품목을 고르거나 저장해서 서버 값이 바뀌면 폼을 새로 그린다 (입력 상태 초기화)
            key={selection.mode === 'edit' ? `${selection.item.id}-${selection.item.updatedAt}` : 'new'}
            item={selection.mode === 'edit' ? selection.item : null}
            notice={notice}
            onSaved={handleSaved}
            onClose={() => select(null)}
          />
        )}
      </div>
    </section>
  )
}
