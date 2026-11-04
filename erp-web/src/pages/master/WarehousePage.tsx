import { useState } from 'react'
import { type WarehouseSearch, warehouseApi } from '../../api/warehouses'
import { ActiveFilterSelect, MasterGrid } from './MasterParts'
import { type ActiveFilter, toActive, useMasterPage } from './useMasterPage'
import { WarehouseForm } from './WarehouseForm'

type Filter = { keyword: string; active: ActiveFilter }

const INITIAL_FILTER: Filter = { keyword: '', active: 'active' }

function toSearch(filter: Filter): WarehouseSearch {
  return { keyword: filter.keyword.trim() || undefined, active: toActive(filter.active) }
}

/** 창고 화면. 재고는 "품목 × 창고" 단위로 쌓이므로(3단계) 입고·출고는 항상 어느 창고인지를 함께 기록한다 */
export function WarehousePage() {
  const [filter, setFilter] = useState<Filter>(INITIAL_FILTER)
  const page = useMasterPage(warehouseApi.search, toSearch(INITIAL_FILTER))

  return (
    <section className="page page-wide">
      <div className="page-header">
        <div>
          <h1>창고</h1>
          <p className="page-desc">
            재고가 놓이는 장소입니다. 같은 품목이라도 창고가 다르면 다른 재고로 관리합니다(3단계). 삭제 대신 사용
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
            placeholder="코드 또는 창고명"
          />
        </label>
        <ActiveFilterSelect value={filter.active} onChange={(active) => setFilter({ ...filter, active })} />
        <button type="submit" className="btn btn-secondary" disabled={page.loading}>
          조회
        </button>
      </form>

      <div className={page.selection ? 'split' : undefined}>
        <MasterGrid
          columns={['코드', '창고명', '위치']}
          rows={page.rows}
          loading={page.loading}
          error={page.error}
          selectedId={page.editing?.id ?? null}
          emptyText="조회된 창고가 없습니다."
          onSelect={(warehouse) => page.select({ mode: 'edit', record: warehouse })}
          renderCells={(warehouse) => (
            <>
              <td>
                <code>{warehouse.code}</code>
              </td>
              <td>{warehouse.name}</td>
              <td>{warehouse.location ?? '-'}</td>
            </>
          )}
        />

        {page.selection && (
          <WarehouseForm
            key={page.formKey}
            warehouse={page.editing}
            notice={page.notice}
            onSaved={page.saved}
            onClose={() => page.select(null)}
          />
        )}
      </div>
    </section>
  )
}
