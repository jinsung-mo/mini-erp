import { useState } from 'react'
import { useSearchParams } from 'react-router'
import {
  type StockTransactionType,
  TRANSACTION_TYPES,
  type TransactionSearch,
  inventoryApi,
} from '../../api/inventory'
import { ErrorAlert } from '../master/MasterParts'
import { useSearch } from '../purchase/useSearch'
import { TransactionTable } from './InventoryParts'
import { useWarehouses } from './useWarehouses'

type Filter = {
  keyword: string
  warehouseId: string
  type: '' | StockTransactionType
  fromDate: string
  toDate: string
}

function toSearch(filter: Filter, itemId: number | undefined): TransactionSearch {
  return {
    keyword: filter.keyword.trim() || undefined,
    itemId,
    warehouseId: filter.warehouseId ? Number(filter.warehouseId) : undefined,
    type: filter.type || undefined,
    fromDate: filter.fromDate || undefined,
    toDate: filter.toDate || undefined,
  }
}

/**
 * 수불 이력(원장): 재고가 들어오고 나간 기록 전부. 고치거나 지우는 기능은 없다.
 * 현재고 화면의 "전체 보기"로 오면 주소의 itemId·warehouseId로 한 품목 × 창고만 본다.
 */
export function StockTransactionPage() {
  const [searchParams] = useSearchParams()
  const itemId = searchParams.get('itemId') ? Number(searchParams.get('itemId')) : undefined
  const [filter, setFilter] = useState<Filter>({
    keyword: '',
    warehouseId: searchParams.get('warehouseId') ?? '',
    type: '',
    fromDate: '',
    toDate: '',
  })
  const list = useSearch(inventoryApi.transactions, toSearch(filter, itemId))
  const warehouses = useWarehouses()
  const itemLabel = itemId !== undefined && list.rows[0] ? `${list.rows[0].item.code} ${list.rows[0].item.name}` : null

  return (
    <section className="page page-wide">
      <div className="page-header">
        <div>
          <h1>수불 이력{itemLabel && <span className="title-sub"> · {itemLabel}</span>}</h1>
          <p className="page-desc">
            입고·조정이 일어날 때마다 한 줄씩 쌓이는 원장입니다. 직후 재고는 그 거래가 끝난 순간의 현재고이고, 잘못된
            기록은 지우지 않고 반대 수량의 조정으로 바로잡습니다.
          </p>
        </div>
      </div>

      <form
        className="filter-bar"
        onSubmit={(event) => {
          event.preventDefault()
          list.search(toSearch(filter, itemId))
        }}
      >
        <label>
          검색어
          <input
            name="keyword"
            value={filter.keyword}
            onChange={(e) => setFilter({ ...filter, keyword: e.target.value })}
            placeholder="문서 번호, 품목 코드·이름"
          />
        </label>
        <label>
          창고
          <select value={filter.warehouseId} onChange={(e) => setFilter({ ...filter, warehouseId: e.target.value })}>
            <option value="">전체</option>
            {warehouses.map((warehouse) => (
              <option key={warehouse.id} value={warehouse.id}>
                {warehouse.code} {warehouse.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          종류
          <select
            name="type"
            value={filter.type}
            onChange={(e) => setFilter({ ...filter, type: e.target.value as Filter['type'] })}
          >
            <option value="">전체</option>
            {TRANSACTION_TYPES.map((type) => (
              <option key={type.value} value={type.value}>
                {type.label}
              </option>
            ))}
          </select>
        </label>
        <label>
          시작일
          <input
            type="date"
            className="date-input"
            value={filter.fromDate}
            onChange={(e) => setFilter({ ...filter, fromDate: e.target.value })}
          />
        </label>
        <label>
          종료일
          <input
            type="date"
            className="date-input"
            value={filter.toDate}
            onChange={(e) => setFilter({ ...filter, toDate: e.target.value })}
          />
        </label>
        <button type="submit" className="btn btn-secondary" disabled={list.loading}>
          조회
        </button>
      </form>

      <div className="grid-panel">
        <div className="grid-meta">{list.loading ? '조회 중…' : `${list.rows.length}건`}</div>
        {list.error ? <ErrorAlert error={list.error} /> : <TransactionTable rows={list.rows} />}
      </div>
    </section>
  )
}
