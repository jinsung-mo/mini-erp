import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import {
  OPEN_SO_STATUSES,
  SO_STATUSES,
  type SalesOrderSearch,
  type SalesOrderStatus,
  salesOrderApi,
} from '../../api/salesOrders'
import { formatDateTime, formatMoney } from '../../format'
import { ErrorAlert } from '../master/MasterParts'
import { SoStatusTag } from './SalesParts'
import { useSearch } from '../purchase/useSearch'

/** 조회 조건의 상태: 전체 / 진행 중(아직 끝나지 않은 수주) / 상태 하나 */
type StatusFilter = 'all' | 'open' | SalesOrderStatus

type Filter = { keyword: string; status: StatusFilter }

const INITIAL_FILTER: Filter = { keyword: '', status: 'open' }

function toSearch(filter: Filter): SalesOrderSearch {
  return {
    keyword: filter.keyword.trim() || undefined,
    status: filter.status === 'all' ? undefined : filter.status === 'open' ? OPEN_SO_STATUSES : [filter.status],
  }
}

/** 수주 목록. 행을 누르면 수주서 화면으로 간다 */
export function SalesOrderListPage() {
  const navigate = useNavigate()
  const [filter, setFilter] = useState<Filter>(INITIAL_FILTER)
  const list = useSearch(salesOrderApi.search, toSearch(INITIAL_FILTER))

  return (
    <section className="page page-wide">
      <div className="page-header">
        <div>
          <h1>수주</h1>
          <p className="page-desc">
            고객에게 받은 주문입니다. 확정하면 서버가 가용 재고(현재고 - 이미 약속한 수량)를 확인하고 재고를 할당하며,
            확정한 수주만 출고할 수 있습니다.
          </p>
        </div>
        <Link to="/sales/orders/new" className="btn">
          신규 수주
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
            placeholder="수주 번호, 고객 코드·이름"
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
            {SO_STATUSES.map((status) => (
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
                <th>수주 번호</th>
                <th>상태</th>
                <th>고객</th>
                <th>출고 창고</th>
                <th>수주일</th>
                <th>납기 요청일</th>
                <th className="num">품목</th>
                <th className="num">금액</th>
                <th>수정자</th>
                <th>수정일시</th>
              </tr>
            </thead>
            <tbody>
              {list.rows.map((so) => (
                <tr
                  key={so.id}
                  onClick={() => navigate(`/sales/orders/${so.id}`)}
                  onKeyDown={(e) => e.key === 'Enter' && navigate(`/sales/orders/${so.id}`)}
                  tabIndex={0}
                >
                  <td>
                    <code>{so.orderNo}</code>
                  </td>
                  <td>
                    <SoStatusTag status={so.status} />
                  </td>
                  <td>{so.customer.name}</td>
                  <td>{so.warehouse.name}</td>
                  <td>{so.orderDate}</td>
                  <td>{so.dueDate ?? '-'}</td>
                  <td className="num">{so.lineCount}</td>
                  <td className="num">{formatMoney(so.totalAmount)}</td>
                  <td>{so.updatedBy}</td>
                  <td>{formatDateTime(so.updatedAt)}</td>
                </tr>
              ))}
              {!list.loading && list.rows.length === 0 && (
                <tr>
                  <td colSpan={10} className="grid-empty">
                    조회된 수주가 없습니다.
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
