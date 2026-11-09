import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import {
  PO_STATUSES,
  type PurchaseOrderSearch,
  type PurchaseOrderStatus,
  purchaseOrderApi,
} from '../../api/purchaseOrders'
import { formatDateTime, formatMoney } from '../../format'
import { ErrorAlert } from '../master/MasterParts'
import { PoStatusTag } from './PurchaseParts'
import { useSearch } from './useSearch'

/** 조회 조건의 상태: 전체 / 진행 중(아직 끝나지 않은 발주) / 상태 하나 */
type StatusFilter = 'all' | 'open' | PurchaseOrderStatus

type Filter = { keyword: string; status: StatusFilter }

const OPEN_STATUSES: PurchaseOrderStatus[] = ['DRAFT', 'SUBMITTED', 'APPROVED', 'PARTIALLY_RECEIVED']

const INITIAL_FILTER: Filter = { keyword: '', status: 'open' }

function toSearch(filter: Filter): PurchaseOrderSearch {
  return {
    keyword: filter.keyword.trim() || undefined,
    status: filter.status === 'all' ? undefined : filter.status === 'open' ? OPEN_STATUSES : [filter.status],
  }
}

/** 발주 목록. 행을 누르면 발주서 화면으로 간다 (줄이 있는 문서라 옆 패널에 넣기에는 좁다) */
export function PurchaseOrderListPage() {
  const navigate = useNavigate()
  const [filter, setFilter] = useState<Filter>(INITIAL_FILTER)
  const list = useSearch(purchaseOrderApi.search, toSearch(INITIAL_FILTER))

  return (
    <section className="page page-wide">
      <div className="page-header">
        <div>
          <h1>발주</h1>
          <p className="page-desc">
            공급처에 물건을 주문하는 문서입니다. 작성 → 승인 요청 → 승인을 거쳐야 입고를 받을 수 있고, 작성한 사람은
            승인할 수 없습니다.
          </p>
        </div>
        <Link to="/purchase/orders/new" className="btn">
          신규 발주
        </Link>
      </div>

      <form
        className="filter-bar"
        onSubmit={(event) => {
          event.preventDefault()
          list.search(toSearch(filter))
        }}
      >
        <label>
          검색어
          <input
            name="keyword"
            value={filter.keyword}
            onChange={(e) => setFilter({ ...filter, keyword: e.target.value })}
            placeholder="발주 번호, 공급처 코드·이름"
          />
        </label>
        <label>
          상태
          <select
            name="status"
            value={filter.status}
            onChange={(e) => setFilter({ ...filter, status: e.target.value as StatusFilter })}
          >
            <option value="open">진행 중</option>
            <option value="all">전체</option>
            {PO_STATUSES.map((status) => (
              <option key={status.value} value={status.value}>
                {status.label}
              </option>
            ))}
          </select>
        </label>
        <button type="submit" className="btn btn-secondary" disabled={list.loading}>
          조회
        </button>
      </form>

      <div className="grid-panel">
        <div className="grid-meta">{list.loading ? '조회 중…' : `${list.rows.length}건`}</div>
        {list.error ? (
          <ErrorAlert error={list.error} />
        ) : (
          <table className="grid">
            <thead>
              <tr>
                <th>발주 번호</th>
                <th>상태</th>
                <th>공급처</th>
                <th>입고 창고</th>
                <th>발주일</th>
                <th>납기 요청일</th>
                <th className="num">품목</th>
                <th className="num">금액</th>
                <th>수정자</th>
                <th>수정일시</th>
              </tr>
            </thead>
            <tbody>
              {list.rows.map((po) => (
                <tr
                  key={po.id}
                  onClick={() => navigate(`/purchase/orders/${po.id}`)}
                  onKeyDown={(e) => e.key === 'Enter' && navigate(`/purchase/orders/${po.id}`)}
                  tabIndex={0}
                >
                  <td>
                    <code>{po.orderNo}</code>
                  </td>
                  <td>
                    <PoStatusTag status={po.status} />
                  </td>
                  <td>{po.supplier.name}</td>
                  <td>{po.warehouse.name}</td>
                  <td>{po.orderDate}</td>
                  <td>{po.dueDate ?? '-'}</td>
                  <td className="num">{po.lineCount}</td>
                  <td className="num">{formatMoney(po.totalAmount)}</td>
                  <td>{po.updatedBy}</td>
                  <td>{formatDateTime(po.updatedAt)}</td>
                </tr>
              ))}
              {!list.loading && list.rows.length === 0 && (
                <tr>
                  <td colSpan={10} className="grid-empty">
                    조회된 발주가 없습니다.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        )}
      </div>
    </section>
  )
}
