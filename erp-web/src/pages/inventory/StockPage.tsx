import { type FormEvent, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { type Stock, type StockSearch, type StockTransaction, inventoryApi } from '../../api/inventory'
import { Field } from '../../components/Field'
import { formatDateTime, formatQuantity, today } from '../../format'
import { ErrorAlert } from '../master/MasterParts'
import { useMasterForm } from '../master/useMasterForm'
import { useSearch } from '../purchase/useSearch'
import { SignedQuantity, TransactionTable } from './InventoryParts'
import { useWarehouses } from './useWarehouses'

type Filter = { keyword: string; warehouseId: string; includeZero: boolean }

const INITIAL_FILTER: Filter = { keyword: '', warehouseId: '', includeZero: false }

function toSearch(filter: Filter): StockSearch {
  return {
    keyword: filter.keyword.trim() || undefined,
    warehouseId: filter.warehouseId ? Number(filter.warehouseId) : undefined,
    includeZero: filter.includeZero,
  }
}

/**
 * 현재고: 품목 × 창고마다 지금 몇 개 있나. 행을 고르면 그 품목의 최근 수불과 실사 조정 입력이 아래에 열린다.
 * 현재고를 직접 고치는 입력은 없다. 수량이 틀렸으면 실사 조정으로 원장에 한 줄을 남겨서 바꾼다.
 */
export function StockPage() {
  const [filter, setFilter] = useState<Filter>(INITIAL_FILTER)
  const list = useSearch(inventoryApi.stocks, toSearch(INITIAL_FILTER))
  const warehouses = useWarehouses()
  const [searchParams, setSearchParams] = useSearchParams()
  const selectedId = searchParams.get('stock') ? Number(searchParams.get('stock')) : null
  const selected = list.rows.find((stock) => stock.id === selectedId) ?? null

  return (
    <section className="page page-wide">
      <div className="page-header">
        <div>
          <h1>현재고</h1>
          <p className="page-desc">
            품목 × 창고별 지금 수량입니다. 입고·출고·실사 조정이 원장에 한 줄씩 쌓이고, 현재고는 그 합계입니다. 할당은
            확정한 수주가 아직 가져가지 않은 수량이고, 가용(현재고 - 할당)이 새 수주에 약속할 수 있는 수량입니다.
          </p>
        </div>
        <Link to="/inventory/transactions" className="btn btn-secondary">
          수불 이력
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
            placeholder="품목 코드·이름"
          />
        </label>
        <label>
          창고
          <select
            name="warehouseId"
            value={filter.warehouseId}
            onChange={(e) => setFilter({ ...filter, warehouseId: e.target.value })}
          >
            <option value="">전체</option>
            {warehouses.map((warehouse) => (
              <option key={warehouse.id} value={warehouse.id}>
                {warehouse.code} {warehouse.name}
              </option>
            ))}
          </select>
        </label>
        <label className="inline-check">
          <input
            type="checkbox"
            name="includeZero"
            checked={filter.includeZero}
            onChange={(e) => setFilter({ ...filter, includeZero: e.target.checked })}
          />
          0인 재고 포함
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
                <th>품목 코드</th>
                <th>품목명</th>
                <th>규격</th>
                <th>단위</th>
                <th>창고</th>
                <th className="num">현재고</th>
                <th className="num">할당</th>
                <th className="num">가용</th>
                <th>마지막 변경</th>
                <th>변경일시</th>
              </tr>
            </thead>
            <tbody>
              {list.rows.map((stock) => (
                <tr
                  key={stock.id}
                  className={stock.id === selectedId ? 'row-selected' : undefined}
                  onClick={() => setSearchParams({ stock: String(stock.id) })}
                  onKeyDown={(e) => e.key === 'Enter' && setSearchParams({ stock: String(stock.id) })}
                  tabIndex={0}
                >
                  <td>
                    <code>{stock.item.code}</code>
                  </td>
                  <td>{stock.item.name}</td>
                  <td>{stock.item.spec ?? '-'}</td>
                  <td>{stock.item.unit}</td>
                  <td>{stock.warehouse.name}</td>
                  <td className="num">
                    <strong>{formatQuantity(stock.quantity)}</strong>
                  </td>
                  <td className="num">{stock.allocatedQuantity === 0 ? '-' : formatQuantity(stock.allocatedQuantity)}</td>
                  <td
                    className={stock.availableQuantity < 0 ? 'num short' : 'num'}
                    title={stock.availableQuantity < 0 ? '할당 부족: 약속한 수량보다 실제 재고가 적습니다' : undefined}
                  >
                    {formatQuantity(stock.availableQuantity)}
                  </td>
                  <td>{stock.updatedBy}</td>
                  <td>{formatDateTime(stock.updatedAt)}</td>
                </tr>
              ))}
              {!list.loading && list.rows.length === 0 && (
                <tr>
                  <td colSpan={10} className="grid-empty">
                    재고가 없습니다. 입고를 처리하면 여기에 나옵니다.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        )}
      </div>

      {selected && <StockDetail key={selected.id} stock={selected} onChanged={list.reload} />}
    </section>
  )
}

/** 고른 품목 × 창고의 최근 수불과 실사 조정 */
function StockDetail({ stock, onChanged }: { stock: Stock; onChanged: () => void }) {
  // 이 컴포넌트는 고른 행마다 key로 새로 만들어지므로, 조회 조건은 처음 한 번만 정하면 된다
  const history = useSearch(inventoryApi.transactions, { itemId: stock.item.id, warehouseId: stock.warehouse.id })
  const [counted, setCounted] = useState('')
  const [adjustDate, setAdjustDate] = useState(today)
  const [reason, setReason] = useState('')
  const [notice, setNotice] = useState<string | null>(null)
  const form = useMasterForm<StockTransaction>(adjusted, {})

  const countedNumber = counted.trim() === '' ? null : Number(counted.replaceAll(',', ''))
  const difference =
    countedNumber !== null && Number.isFinite(countedNumber) ? round3(countedNumber - stock.quantity) : null

  function adjusted(t: StockTransaction) {
    setNotice(`${t.sourceNo}로 조정했습니다. (${t.quantity > 0 ? '+' : ''}${formatQuantity(t.quantity)})`)
    setCounted('')
    setReason('')
    onChanged()
    history.reload()
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setNotice(null)
    void form.run(
      () =>
        inventoryApi.adjust({
          itemId: stock.item.id,
          warehouseId: stock.warehouse.id,
          bookQuantity: stock.quantity,
          countedQuantity: countedNumber !== null && Number.isFinite(countedNumber) ? countedNumber : null,
          adjustDate,
          reason,
        }),
      '',
    )
  }

  function refresh() {
    setNotice('최신 재고를 다시 불러왔습니다. 장부 수량을 확인하고 실사 수량을 다시 입력하세요.')
    onChanged()
    history.reload()
  }

  const stale = form.generalError?.code === 'CONCURRENT_MODIFICATION'

  return (
    <div className="stock-detail">
      <section className="panel">
        <div className="panel-title">
          <h2>
            실사 조정 <code className="form-code">{stock.item.code}</code>
          </h2>
          <span className="field-hint">
            {stock.item.name} · {stock.warehouse.name}
          </span>
        </div>

        {notice && <div className="notice doc-message">{notice}</div>}
        {form.generalError && (
          <div className="doc-message">
            <ErrorAlert error={form.generalError} />
            {stale && (
              <button type="button" className="btn btn-secondary" onClick={refresh}>
                다시 조회
              </button>
            )}
          </div>
        )}

        <form onSubmit={handleSubmit} noValidate className="adjust-form">
          <Field label="장부 수량" hint="화면을 연 시점의 현재고">
            <div className="readonly">
              {formatQuantity(stock.quantity)} {stock.item.unit}
            </div>
          </Field>
          <Field label="실사 수량" error={form.fieldError('countedQuantity')}>
            <input
              aria-label="실사 수량"
              className="num-input"
              inputMode="decimal"
              value={counted}
              onChange={(e) => setCounted(e.target.value)}
            />
          </Field>
          <Field label="차이">
            <div className="readonly">
              {difference === null ? '-' : difference === 0 ? '차이 없음' : <SignedQuantity value={difference} />}
            </div>
          </Field>
          <Field label="조정일" error={form.fieldError('adjustDate')}>
            <input type="date" value={adjustDate} onChange={(e) => setAdjustDate(e.target.value)} />
          </Field>
          <Field label="사유" error={form.fieldError('reason')}>
            <input
              aria-label="조정 사유"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              maxLength={500}
              placeholder="예: 파손 2개 폐기"
            />
          </Field>
          <div className="doc-actions">
            <button type="submit" className="btn" disabled={form.busy}>
              조정 반영
            </button>
          </div>
        </form>
        <p className="field-hint">
          센 수량을 넣으면 차이만큼 원장에 조정 한 줄이 쌓입니다. 화면을 연 뒤 입고가 있었다면 서버가 거절하니 다시
          조회하세요.
        </p>
      </section>

      <h2 className="section-title">
        최근 수불{' '}
        <Link
          className="section-link"
          to={`/inventory/transactions?itemId=${stock.item.id}&warehouseId=${stock.warehouse.id}`}
        >
          전체 보기
        </Link>
      </h2>
      <div className="grid-panel">
        {history.error ? (
          <ErrorAlert error={history.error} />
        ) : (
          <TransactionTable rows={history.rows.slice(0, 10)} showItem={false} />
        )}
      </div>
    </div>
  )
}

/** 0.1 + 0.2 같은 부동소수 오차를 화면 표시용으로만 정리한다. 실제 차이는 서버가 BigDecimal로 계산한다 */
function round3(value: number): number {
  return Math.round(value * 1000) / 1000
}
