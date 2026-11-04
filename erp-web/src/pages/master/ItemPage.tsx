import { useState } from 'react'
import { type ItemSearch, itemApi } from '../../api/items'
import { ItemForm } from './ItemForm'
import { ActiveFilterSelect, MasterGrid } from './MasterParts'
import { type ActiveFilter, toActive, useMasterPage } from './useMasterPage'

type Filter = { keyword: string; active: ActiveFilter }

const INITIAL_FILTER: Filter = { keyword: '', active: 'active' }

function toSearch(filter: Filter): ItemSearch {
  return { keyword: filter.keyword.trim() || undefined, active: toActive(filter.active) }
}

/**
 * 품목 화면: 왼쪽 목록(조회) + 오른쪽 입력 폼(등록·수정). ERP 기준정보 화면의 전형적인 구성이다.
 * 조회·선택·저장 흐름은 useMasterPage가, 그리드와 폼의 틀은 MasterParts가 맡는다. 여기에는 품목에만 있는 것만 둔다.
 */
export function ItemPage() {
  const [filter, setFilter] = useState<Filter>(INITIAL_FILTER)
  const page = useMasterPage(itemApi.search, toSearch(INITIAL_FILTER))

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
        <button type="button" className="btn" onClick={() => page.select({ mode: 'create' })}>
          신규 등록
        </button>
      </div>

      <form
        className="filter-bar"
        onSubmit={(event) => {
          event.preventDefault()
          page.search(toSearch(filter))
        }}
      >
        <label>
          검색어
          <input
            name="keyword"
            value={filter.keyword}
            onChange={(e) => setFilter({ ...filter, keyword: e.target.value })}
            placeholder="코드 또는 품목명"
          />
        </label>
        <ActiveFilterSelect value={filter.active} onChange={(active) => setFilter({ ...filter, active })} />
        <button type="submit" className="btn btn-secondary" disabled={page.loading}>
          조회
        </button>
      </form>

      <div className={page.selection ? 'split' : undefined}>
        <MasterGrid
          columns={['코드', '품목명', '규격', '단위']}
          rows={page.rows}
          loading={page.loading}
          error={page.error}
          selectedId={page.editing?.id ?? null}
          emptyText="조회된 품목이 없습니다."
          onSelect={(item) => page.select({ mode: 'edit', record: item })}
          renderCells={(item) => (
            <>
              <td>
                <code>{item.code}</code>
              </td>
              <td>{item.name}</td>
              <td>{item.spec ?? '-'}</td>
              <td>{item.unit}</td>
            </>
          )}
        />

        {page.selection && (
          <ItemForm
            key={page.formKey}
            item={page.editing}
            notice={page.notice}
            onSaved={page.saved}
            onClose={() => page.select(null)}
          />
        )}
      </div>
    </section>
  )
}
