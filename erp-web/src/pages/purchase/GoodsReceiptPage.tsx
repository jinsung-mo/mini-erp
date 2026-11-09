import { type FormEvent, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { type ApiError, toApiError } from '../../api/client'
import { type GoodsReceipt, type GoodsReceiptSearch, goodsReceiptApi } from '../../api/goodsReceipts'
import { type PurchaseOrder, type PurchaseOrderSearch, purchaseOrderApi } from '../../api/purchaseOrders'
import { Field } from '../../components/Field'
import { formatDateTime, formatQuantity, today } from '../../format'
import { ErrorAlert } from '../master/MasterParts'
import { useMasterForm } from '../master/useMasterForm'
import { PoStatusTag } from './PurchaseParts'
import { useSearch } from './useSearch'

/** 입고를 받을 수 있는 발주: 승인, 부분 입고 */
const RECEIVABLE: PurchaseOrderSearch = { status: ['APPROVED', 'PARTIALLY_RECEIVED'] }
const ALL_RECEIPTS: GoodsReceiptSearch = {}

/**
 * 입고 화면: 위는 입고 대기 발주, 가운데는 고른 발주의 입고 입력, 아래는 입고 내역.
 * 창고 담당자는 물건이 들어오면 발주를 고르고, 실제로 받은 수량을 줄마다 적는다. 발주보다 덜 받으면 부분 입고가 된다.
 */
export function GoodsReceiptPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const selectedId = searchParams.get('order') ? Number(searchParams.get('order')) : null
  const pending = useSearch(purchaseOrderApi.search, RECEIVABLE)
  const history = useSearch(goodsReceiptApi.search, ALL_RECEIPTS)

  function afterReceipt() {
    pending.reload()
    history.reload()
  }

  return (
    <section className="page page-wide">
      <div className="page-header">
        <div>
          <h1>입고</h1>
          <p className="page-desc">
            승인된 발주의 물건이 들어오면 받은 수량을 기록합니다. 한 발주를 여러 번 나눠 받을 수 있고, 남은 수량보다 많이
            받을 수는 없습니다.
          </p>
        </div>
      </div>

      <div className="grid-panel">
        <div className="grid-meta">입고 대기 발주 {pending.loading ? '조회 중…' : `${pending.rows.length}건`}</div>
        {pending.error ? (
          <ErrorAlert error={pending.error} />
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
                    <PoStatusTag status={po.status} />
                  </td>
                  <td>{po.supplier.name}</td>
                  <td>{po.warehouse.name}</td>
                  <td>{po.orderDate}</td>
                  <td>{po.dueDate ?? '-'}</td>
                </tr>
              ))}
              {!pending.loading && pending.rows.length === 0 && (
                <tr>
                  <td colSpan={6} className="grid-empty">
                    입고를 기다리는 발주가 없습니다. 발주가 승인되면 여기에 나옵니다.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        )}
      </div>

      {selectedId !== null && <ReceiptForm key={selectedId} orderId={selectedId} onReceived={afterReceipt} />}

      <h2 className="section-title">입고 내역</h2>
      <div className="grid-panel">
        <div className="grid-meta">{history.loading ? '조회 중…' : `${history.rows.length}건`}</div>
        {history.error ? (
          <ErrorAlert error={history.error} />
        ) : (
          <table className="grid">
            <thead>
              <tr>
                <th>입고 번호</th>
                <th>입고일</th>
                <th>발주 번호</th>
                <th>공급처</th>
                <th>창고</th>
                <th>품목</th>
                <th>메모</th>
                <th>처리자</th>
                <th>처리일시</th>
              </tr>
            </thead>
            <tbody>
              {history.rows.map((receipt) => (
                <tr key={receipt.id} className="row-static">
                  <td>
                    <code>{receipt.receiptNo}</code>
                  </td>
                  <td>{receipt.receiptDate}</td>
                  <td>
                    <Link to={`/purchase/orders/${receipt.purchaseOrder.id}`}>{receipt.purchaseOrder.orderNo}</Link>
                  </td>
                  <td>{receipt.supplier.name}</td>
                  <td>{receipt.warehouse.name}</td>
                  <td>{summarize(receipt)}</td>
                  <td>{receipt.memo ?? '-'}</td>
                  <td>{receipt.createdBy}</td>
                  <td>{formatDateTime(receipt.createdAt)}</td>
                </tr>
              ))}
              {!history.loading && history.rows.length === 0 && (
                <tr>
                  <td colSpan={9} className="grid-empty">
                    입고 내역이 없습니다.
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
function summarize(receipt: GoodsReceipt): string {
  const [first, ...rest] = receipt.lines
  if (!first) return '-'
  const head = `${first.item.name} ${formatQuantity(first.quantity)}`
  return rest.length > 0 ? `${head} 외 ${rest.length}건` : head
}

function ReceiptForm({ orderId, onReceived }: { orderId: number; onReceived: () => void }) {
  const [po, setPo] = useState<PurchaseOrder | null>(null)
  const [loadError, setLoadError] = useState<ApiError | null>(null)
  const [receiptDate, setReceiptDate] = useState(today)
  const [memo, setMemo] = useState('')
  /** 발주 줄 id → 이번에 받을 수량 (입력 문자열). 처음에는 남은 수량을 그대로 채운다 */
  const [quantities, setQuantities] = useState<Record<number, string>>({})
  /** 서버로 보낸 줄 순서. 오류 위치(lines[1].quantity)를 화면의 줄로 되돌릴 때 쓴다 */
  const [sentLineIds, setSentLineIds] = useState<number[]>([])
  const [notice, setNotice] = useState<string | null>(null)
  const form = useMasterForm<GoodsReceipt>(received, {})

  function load(next: PurchaseOrder) {
    setPo(next)
    setQuantities(
      Object.fromEntries(
        next.lines.filter((line) => line.remainingQuantity > 0).map((line) => [line.id, String(line.remainingQuantity)]),
      ),
    )
  }

  useEffect(() => {
    let ignore = false
    purchaseOrderApi.get(orderId).then(
      (result) => !ignore && load(result),
      (e: unknown) => !ignore && setLoadError(toApiError(e)),
    )
    return () => {
      ignore = true
    }
  }, [orderId])

  function received(receipt: GoodsReceipt) {
    setNotice(`${receipt.receiptNo}로 입고했습니다.`)
    setMemo('')
    onReceived()
    purchaseOrderApi.get(orderId).then(load, (e: unknown) => setLoadError(toApiError(e)))
  }

  if (loadError) return <ErrorAlert error={loadError} />
  if (!po) return <p className="page-desc">발주를 불러오는 중…</p>

  const receivable = po.status === 'APPROVED' || po.status === 'PARTIALLY_RECEIVED'

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!po) return
    setNotice(null)
    // 빈 칸인 줄은 이번에 받지 않은 것으로 보고 보내지 않는다
    const lines = po.lines
      .filter((line) => (quantities[line.id] ?? '').trim() !== '')
      .map((line) => {
        const number = Number(quantities[line.id].replaceAll(',', ''))
        return { purchaseOrderLineId: line.id, quantity: Number.isFinite(number) ? number : null }
      })
    setSentLineIds(lines.map((line) => line.purchaseOrderLineId))
    void form.run(() => goodsReceiptApi.receive(po.id, { receiptDate, memo, lines }), '')
  }

  function lineError(lineId: number): string | undefined {
    const index = sentLineIds.indexOf(lineId)
    return index < 0 ? undefined : form.fieldError(`lines[${index}].quantity`)
  }

  return (
    <section className="panel receipt-form">
      <div className="panel-title">
        <h2>
          입고 처리 <code className="form-code">{po.orderNo}</code> <PoStatusTag status={po.status} />
        </h2>
        <span className="field-hint">
          {po.supplier.name} → {po.warehouse.name}
        </span>
      </div>

      {notice && <div className="notice doc-message">{notice}</div>}
      {form.generalError && (
        <div className="doc-message">
          <ErrorAlert error={form.generalError} />
        </div>
      )}
      {!receivable && (
        <p className="field-hint">
          이 발주는 지금 입고할 수 없는 상태입니다. <Link to={`/purchase/orders/${po.id}`}>발주서 보기</Link>
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
                <th className="num">발주 수량</th>
                <th className="num">받은 수량</th>
                <th className="num">남은 수량</th>
                <th className="num">이번 입고</th>
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
                    <td className="num">{formatQuantity(line.receivedQuantity)}</td>
                    <td className="num">{formatQuantity(line.remainingQuantity)}</td>
                    <td className={error ? 'num field-invalid' : 'num'}>
                      {done ? (
                        <span className="field-hint">완료</span>
                      ) : (
                        <input
                          aria-label={`${line.lineNo}번 줄 이번 입고 수량`}
                          className="num-input"
                          inputMode="decimal"
                          value={quantities[line.id] ?? ''}
                          onChange={(e) => setQuantities({ ...quantities, [line.id]: e.target.value })}
                          disabled={!receivable}
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
          <Field label="입고일" error={form.fieldError('receiptDate')}>
            <input type="date" value={receiptDate} onChange={(e) => setReceiptDate(e.target.value)} />
          </Field>
          <Field label="메모" error={form.fieldError('memo')}>
            <input value={memo} onChange={(e) => setMemo(e.target.value)} maxLength={500} placeholder="예: 1차분" />
          </Field>
          <div className="doc-actions">
            <button type="submit" className="btn" disabled={form.busy || !receivable}>
              입고 처리
            </button>
            <Link to={`/purchase/orders/${po.id}`} className="btn btn-secondary">
              발주서 보기
            </Link>
          </div>
        </div>
        {form.fieldError('lines') && <div className="field-error">{form.fieldError('lines')}</div>}
        <p className="field-hint">받지 않은 줄은 칸을 비워 둡니다. 남은 수량보다 많이 적으면 서버가 거절합니다.</p>
      </form>
    </section>
  )
}
