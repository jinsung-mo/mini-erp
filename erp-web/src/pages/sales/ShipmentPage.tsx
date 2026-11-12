import { type FormEvent, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { type ApiError, toApiError } from '../../api/client'
import { type SalesOrder, type SalesOrderSearch, salesOrderApi } from '../../api/salesOrders'
import { type Shipment, type ShipmentSearch, shipmentApi } from '../../api/shipments'
import { Field } from '../../components/Field'
import { formatDateTime, formatQuantity, today } from '../../format'
import { ErrorAlert } from '../master/MasterParts'
import { useMasterForm } from '../master/useMasterForm'
import { useSearch } from '../purchase/useSearch'
import { SoStatusTag } from './SalesParts'

/** 출고할 수 있는 수주: 확정, 부분 출고 (재고가 할당돼 있는 상태) */
const SHIPPABLE: SalesOrderSearch = { status: ['CONFIRMED', 'PARTIALLY_SHIPPED'] }
const ALL_SHIPMENTS: ShipmentSearch = {}

/**
 * 출고 화면: 위는 출고 대기 수주, 가운데는 고른 수주의 출고 입력, 아래는 출고 내역 (입고 화면과 같은 배치).
 * 확정 때 이미 재고를 할당해 두었으므로, 출고는 그 할당분을 실제로 내보내는 일이다.
 */
export function ShipmentPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const selectedId = searchParams.get('order') ? Number(searchParams.get('order')) : null
  const pending = useSearch(salesOrderApi.search, SHIPPABLE)
  const history = useSearch(shipmentApi.search, ALL_SHIPMENTS)

  function afterShipment() {
    pending.reload()
    history.reload()
  }

  return (
    <section className="page page-wide">
      <div className="page-header">
        <div>
          <h1>출고</h1>
          <p className="page-desc">
            확정된 수주의 물건을 내보내고 수량을 기록합니다. 현재고와 할당량이 같은 수량만큼 함께 줄고, 원장에 출고가
            남습니다. 한 수주를 여러 번 나눠 내보낼 수 있습니다.
          </p>
        </div>
      </div>

      <div className="grid-panel">
        <div className="grid-meta">출고 대기 수주 {pending.loading ? '조회 중…' : `${pending.rows.length}건`}</div>
        {pending.error ? (
          <ErrorAlert error={pending.error} />
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
              </tr>
            </thead>
            <tbody>
              {pending.rows.map((po) => (
                <tr
                  key={po.id}
                  className={po.id === selectedId ? 'row-selected' : undefined}
                  onClick={() => setSearchParams({ order: String(po.id) })}
                  onKeyDown={(e) => e.key === 'Enter' && setSearchParams({ order: String(po.id) })}
                  tabIndex={0}
                >
                  <td>
                    <code>{po.orderNo}</code>
                  </td>
                  <td>
                    <SoStatusTag status={po.status} />
                  </td>
                  <td>{po.customer.name}</td>
                  <td>{po.warehouse.name}</td>
                  <td>{po.orderDate}</td>
                  <td>{po.dueDate ?? '-'}</td>
                </tr>
              ))}
              {!pending.loading && pending.rows.length === 0 && (
                <tr>
                  <td colSpan={6} className="grid-empty">
                    출고를 기다리는 수주가 없습니다. 수주를 확정하면 여기에 나옵니다.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        )}
      </div>

      {selectedId !== null && <ShipmentForm key={selectedId} orderId={selectedId} onShipped={afterShipment} />}

      <h2 className="section-title">출고 내역</h2>
      <div className="grid-panel">
        <div className="grid-meta">{history.loading ? '조회 중…' : `${history.rows.length}건`}</div>
        {history.error ? (
          <ErrorAlert error={history.error} />
        ) : (
          <table className="grid">
            <thead>
              <tr>
                <th>출고 번호</th>
                <th>출고일</th>
                <th>수주 번호</th>
                <th>고객</th>
                <th>창고</th>
                <th>품목</th>
                <th>메모</th>
                <th>처리자</th>
                <th>처리일시</th>
              </tr>
            </thead>
            <tbody>
              {history.rows.map((shipment) => (
                <tr key={shipment.id} className="row-static">
                  <td>
                    <code>{shipment.shipmentNo}</code>
                  </td>
                  <td>{shipment.shipDate}</td>
                  <td>
                    <Link to={`/sales/orders/${shipment.salesOrder.id}`}>{shipment.salesOrder.orderNo}</Link>
                  </td>
                  <td>{shipment.customer.name}</td>
                  <td>{shipment.warehouse.name}</td>
                  <td>{summarize(shipment)}</td>
                  <td>{shipment.memo ?? '-'}</td>
                  <td>{shipment.createdBy}</td>
                  <td>{formatDateTime(shipment.createdAt)}</td>
                </tr>
              ))}
              {!history.loading && history.rows.length === 0 && (
                <tr>
                  <td colSpan={9} className="grid-empty">
                    출고 내역이 없습니다.
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

/** "육각볼트 60 외 1건" */
function summarize(shipment: Shipment): string {
  const [first, ...rest] = shipment.lines
  if (!first) return '-'
  const head = `${first.item.name} ${formatQuantity(first.quantity)}`
  return rest.length > 0 ? `${head} 외 ${rest.length}건` : head
}

function ShipmentForm({ orderId, onShipped }: { orderId: number; onShipped: () => void }) {
  const [po, setPo] = useState<SalesOrder | null>(null)
  const [loadError, setLoadError] = useState<ApiError | null>(null)
  const [shipDate, setShipDate] = useState(today)
  const [memo, setMemo] = useState('')
  /** 수주 줄 id → 이번에 내보낼 수량 (입력 문자열). 처음에는 남은 수량을 그대로 채운다 */
  const [quantities, setQuantities] = useState<Record<number, string>>({})
  /** 서버로 보낸 줄 순서. 오류 위치(lines[1].quantity)를 화면의 줄로 되돌릴 때 쓴다 */
  const [sentLineIds, setSentLineIds] = useState<number[]>([])
  const [notice, setNotice] = useState<string | null>(null)
  const form = useMasterForm<Shipment>(shipped, {})

  function load(next: SalesOrder) {
    setPo(next)
    setQuantities(
      Object.fromEntries(
        next.lines.filter((line) => line.remainingQuantity > 0).map((line) => [line.id, String(line.remainingQuantity)]),
      ),
    )
  }

  useEffect(() => {
    let ignore = false
    salesOrderApi.get(orderId).then(
      (result) => !ignore && load(result),
      (e: unknown) => !ignore && setLoadError(toApiError(e)),
    )
    return () => {
      ignore = true
    }
  }, [orderId])

  function shipped(shipment: Shipment) {
    setNotice(`${shipment.shipmentNo}로 출고했습니다.`)
    setMemo('')
    onShipped()
    salesOrderApi.get(orderId).then(load, (e: unknown) => setLoadError(toApiError(e)))
  }

  if (loadError) return <ErrorAlert error={loadError} />
  if (!po) return <p className="page-desc">수주를 불러오는 중…</p>

  const shippable = po.status === 'CONFIRMED' || po.status === 'PARTIALLY_SHIPPED'

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!po) return
    setNotice(null)
    // 빈 칸인 줄은 이번에 내보내지 않은 것으로 보고 보내지 않는다
    const lines = po.lines
      .filter((line) => (quantities[line.id] ?? '').trim() !== '')
      .map((line) => {
        const number = Number(quantities[line.id].replaceAll(',', ''))
        return { salesOrderLineId: line.id, quantity: Number.isFinite(number) ? number : null }
      })
    setSentLineIds(lines.map((line) => line.salesOrderLineId))
    void form.run(() => shipmentApi.ship(po.id, { shipDate, memo, lines }), '')
  }

  function lineError(lineId: number): string | undefined {
    const index = sentLineIds.indexOf(lineId)
    return index < 0 ? undefined : form.fieldError(`lines[${index}].quantity`)
  }

  return (
    <section className="panel receipt-form">
      <div className="panel-title">
        <h2>
          출고 처리 <code className="form-code">{po.orderNo}</code> <SoStatusTag status={po.status} />
        </h2>
        <span className="field-hint">
          {po.warehouse.name} → {po.customer.name}
        </span>
      </div>

      {notice && <div className="notice doc-message">{notice}</div>}
      {form.generalError && (
        <div className="doc-message">
          <ErrorAlert error={form.generalError} />
        </div>
      )}
      {!shippable && (
        <p className="field-hint">
          이 수주는 지금 출고할 수 없는 상태입니다. <Link to={`/sales/orders/${po.id}`}>수주서 보기</Link>
        </p>
      )}

      <form onSubmit={handleSubmit} noValidate>
        <div className="table-scroll">
          <table className="grid lines">
            <thead>
              <tr>
                <th className="num">#</th>
                <th>품목</th>
                <th>단위</th>
                <th className="num">수주 수량</th>
                <th className="num">출고한 수량</th>
                <th className="num">남은 수량 (할당)</th>
                <th className="num">이번 출고</th>
              </tr>
            </thead>
            <tbody>
              {po.lines.map((line) => {
                const error = lineError(line.id)
                const done = line.remainingQuantity <= 0
                return (
                  <tr key={line.id} className={done ? 'row-done' : undefined}>
                    <td className="num">{line.lineNo}</td>
                    <td>
                      <code>{line.item.code}</code> {line.item.name}
                    </td>
                    <td>{line.item.unit}</td>
                    <td className="num">{formatQuantity(line.quantity)}</td>
                    <td className="num">{formatQuantity(line.shippedQuantity)}</td>
                    <td className="num">{formatQuantity(line.remainingQuantity)}</td>
                    <td className={error ? 'num field-invalid' : 'num'}>
                      {done ? (
                        <span className="field-hint">완료</span>
                      ) : (
                        <input
                          aria-label={`${line.lineNo}번 줄 이번 출고 수량`}
                          className="num-input"
                          inputMode="decimal"
                          value={quantities[line.id] ?? ''}
                          onChange={(e) => setQuantities({ ...quantities, [line.id]: e.target.value })}
                          disabled={!shippable}
                        />
                      )}
                      {error && <div className="field-error">{error}</div>}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>

        <div className="receipt-fields">
          <Field label="출고일" error={form.fieldError('shipDate')}>
            <input type="date" value={shipDate} onChange={(e) => setShipDate(e.target.value)} />
          </Field>
          <Field label="메모" error={form.fieldError('memo')}>
            <input value={memo} onChange={(e) => setMemo(e.target.value)} maxLength={500} placeholder="예: 1차분" />
          </Field>
          <div className="doc-actions">
            <button type="submit" className="btn" disabled={form.busy || !shippable}>
              출고 처리
            </button>
            <Link to={`/sales/orders/${po.id}`} className="btn btn-secondary">
              수주서 보기
            </Link>
          </div>
        </div>
        {form.fieldError('lines') && <div className="field-error">{form.fieldError('lines')}</div>}
        <p className="field-hint">내보내지 않는 줄은 칸을 비워 둡니다. 남은 수량보다 많거나 창고에 실제로 없는 수량이면 서버가 거절합니다.</p>
      </form>
    </section>
  )
}
