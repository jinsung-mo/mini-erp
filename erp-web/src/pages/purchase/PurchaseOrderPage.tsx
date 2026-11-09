import { type FormEvent, useEffect, useRef, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { toApiError, type ApiError } from '../../api/client'
import { type GoodsReceipt, goodsReceiptApi } from '../../api/goodsReceipts'
import { type Item, itemApi } from '../../api/items'
import { type Partner, partnerApi } from '../../api/partners'
import {
  type PurchaseOrder,
  type PurchaseOrderInput,
  purchaseOrderApi,
  type Ref,
} from '../../api/purchaseOrders'
import { type Warehouse, warehouseApi } from '../../api/warehouses'
import { Field } from '../../components/Field'
import { formatDateTime, formatMoney, formatQuantity, today } from '../../format'
import { getOperatorId } from '../../session/operator'
import { ErrorAlert } from '../master/MasterParts'
import { useMasterForm } from '../master/useMasterForm'
import { EventTimeline, PoStatusTag } from './PurchaseParts'

/** 화면 입력 상태. 입력 칸은 문자열로 들고 있다가 보낼 때 숫자로 바꾼다 (빈 칸과 0을 구분해야 한다) */
type LineValues = { key: number; itemId: string; quantity: string; unitPrice: string }

type Values = {
  supplierId: string
  warehouseId: string
  orderDate: string
  dueDate: string
  memo: string
  lines: LineValues[]
}

type Options = { suppliers: Partner[]; warehouses: Warehouse[]; items: Item[] }

let lineKey = 0

function newLine(): LineValues {
  lineKey += 1
  return { key: lineKey, itemId: '', quantity: '', unitPrice: '' }
}

function emptyValues(): Values {
  return { supplierId: '', warehouseId: '', orderDate: today(), dueDate: '', memo: '', lines: [newLine()] }
}

function fromPo(po: PurchaseOrder): Values {
  return {
    supplierId: String(po.supplier.id),
    warehouseId: String(po.warehouse.id),
    orderDate: po.orderDate,
    dueDate: po.dueDate ?? '',
    memo: po.memo ?? '',
    lines: po.lines.map((line) => ({
      ...newLine(),
      itemId: String(line.item.id),
      quantity: String(line.quantity),
      unitPrice: String(line.unitPrice),
    })),
  }
}

/** 빈 칸은 null로 보낸다. 서버가 "입력하세요" 오류를 해당 칸에 돌려준다 */
function toNumber(value: string): number | null {
  const trimmed = value.trim()
  if (trimmed === '') return null
  const number = Number(trimmed.replaceAll(',', ''))
  return Number.isFinite(number) ? number : null
}

function toInput(values: Values): PurchaseOrderInput {
  return {
    supplierId: toNumber(values.supplierId),
    warehouseId: toNumber(values.warehouseId),
    orderDate: values.orderDate,
    dueDate: values.dueDate || null,
    memo: values.memo,
    lines: values.lines.map((line) => ({
      itemId: toNumber(line.itemId),
      quantity: toNumber(line.quantity),
      unitPrice: toNumber(line.unitPrice),
    })),
  }
}

/** 사용 중지된 기준정보는 선택 목록에 없지만, 이미 저장된 발주가 가리키고 있으면 보여 줘야 한다 */
function withCurrent<T extends { id: number }>(options: T[], current: (Ref & Partial<T>)[]): (T | Ref)[] {
  const missing = current.filter((ref) => !options.some((option) => option.id === ref.id))
  return [...options, ...missing]
}

/** 라우트: /purchase/orders/new, /purchase/orders/:id. 발주가 바뀌면 화면 상태를 새로 시작한다 */
export function PurchaseOrderPage() {
  const { id } = useParams()
  const orderId = id === undefined || id === 'new' ? null : Number(id)
  return <PurchaseOrderDocument key={id} orderId={orderId} />
}

function PurchaseOrderDocument({ orderId }: { orderId: number | null }) {
  const navigate = useNavigate()
  const location = useLocation()
  const [po, setPo] = useState<PurchaseOrder | null>(null)
  const [values, setValues] = useState<Values>(emptyValues)
  const [loading, setLoading] = useState(orderId !== null)
  const [loadError, setLoadError] = useState<ApiError | null>(null)
  const [notice, setNotice] = useState<string | null>(
    (location.state as { notice?: string } | null)?.notice ?? null,
  )
  const [receipts, setReceipts] = useState<GoodsReceipt[]>([])
  const [options, setOptions] = useState<Options>({ suppliers: [], warehouses: [], items: [] })
  const receiptSeq = useRef(0)

  const form = useMasterForm<PurchaseOrder>(saved, { NOT_A_SUPPLIER: 'supplierId' })

  function saved(next: PurchaseOrder, message: string) {
    if (orderId === null) {
      // 등록하면 주소를 발주 번호의 주소로 바꾼다 (새로고침해도 같은 발주가 보이게)
      navigate(`/purchase/orders/${next.id}`, { replace: true, state: { notice: message } })
      return
    }
    setPo(next)
    setValues(fromPo(next))
    setNotice(message)
    loadReceipts(next.id)
  }

  function loadReceipts(id: number) {
    const seq = ++receiptSeq.current
    goodsReceiptApi.search({ purchaseOrderId: id }).then(
      (result) => seq === receiptSeq.current && setReceipts(result),
      () => seq === receiptSeq.current && setReceipts([]),
    )
  }

  useEffect(() => {
    if (orderId === null) return
    let ignore = false
    purchaseOrderApi.get(orderId).then(
      (result) => {
        if (ignore) return
        setPo(result)
        setValues(fromPo(result))
        setLoading(false)
      },
      (e: unknown) => {
        if (ignore) return
        setLoadError(toApiError(e))
        setLoading(false)
      },
    )
    loadReceipts(orderId)
    return () => {
      ignore = true
    }
  }, [orderId])

  const editable = po === null || po.status === 'DRAFT'

  // 작성 중일 때만 선택 목록이 필요하다. 사용 중인 기준정보만, 거래처는 공급처만 고른다
  useEffect(() => {
    if (!editable) return
    let ignore = false
    Promise.all([
      partnerApi.search({ role: 'SUPPLIER', active: true }),
      warehouseApi.search({ active: true }),
      itemApi.search({ active: true }),
    ]).then(
      ([suppliers, warehouses, items]) => !ignore && setOptions({ suppliers, warehouses, items }),
      () => {
        // 선택 목록을 못 불러와도 저장할 때 서버가 알려 준다
      },
    )
    return () => {
      ignore = true
    }
  }, [editable])

  if (loading) return <p className="page-desc">불러오는 중…</p>
  if (loadError) {
    return (
      <section className="page">
        <ErrorAlert error={loadError} />
        <Link to="/purchase/orders">발주 목록으로</Link>
      </section>
    )
  }

  const dirty = po !== null && JSON.stringify(toInput(values)) !== JSON.stringify(toInput(fromPo(po)))

  function set<K extends keyof Values>(key: K, value: Values[K]) {
    setValues((prev) => ({ ...prev, [key]: value }))
  }

  function setLine(key: number, patch: Partial<LineValues>) {
    setValues((prev) => ({ ...prev, lines: prev.lines.map((line) => (line.key === key ? { ...line, ...patch } : line)) }))
  }

  function handleSave(event: FormEvent) {
    event.preventDefault()
    setNotice(null)
    const input = toInput(values)
    if (po === null) {
      void form.run(() => purchaseOrderApi.create(input), '등록했습니다. 내용을 확인하고 승인을 요청하세요.')
    } else {
      void form.run(() => purchaseOrderApi.update(po.id, po.version, input), '저장했습니다.')
    }
  }

  function act(action: () => Promise<PurchaseOrder>, message: string) {
    setNotice(null)
    void form.run(action, message)
  }

  /** 반려·취소·마감은 사유가 필요하다. 빈 사유는 서버가 거절하고 오류를 보여 준다 */
  function actWithReason(label: string, action: (reason: string) => Promise<PurchaseOrder>, message: string) {
    const reason = window.prompt(`${label} 사유를 입력하세요.`)
    if (reason === null) return
    act(() => action(reason), message)
  }

  const itemOptions = withCurrent(options.items, po?.lines.map((line) => line.item) ?? [])
  const supplierOptions = withCurrent(options.suppliers, po ? [po.supplier] : [])
  const warehouseOptions = withCurrent(options.warehouses, po ? [po.warehouse] : [])
  const itemById = new Map(itemOptions.map((item) => [item.id, item]))

  const operator = getOperatorId()
  const lastSubmitter = po?.events.filter((e) => e.type === 'SUBMITTED').at(-1)?.actor
  const selfApproval = po !== null && (operator === po.createdBy || operator === lastSubmitter)

  const estimatedTotal = values.lines.reduce((sum, line) => {
    const quantity = toNumber(line.quantity)
    const price = toNumber(line.unitPrice)
    return quantity !== null && price !== null ? sum + Math.round(quantity * price) : sum
  }, 0)

  return (
    <section className="page page-wide">
      <div className="page-header">
        <div>
          <h1 className="doc-title">
            {po ? '발주서' : '신규 발주'}
            {po && <code className="form-code">{po.orderNo}</code>}
            {po && <PoStatusTag status={po.status} />}
          </h1>
          <p className="page-desc">
            {editable
              ? '작성 중인 발주는 자유롭게 고칠 수 있습니다. 승인 요청 뒤에는 고칠 수 없고, 반려되면 다시 고칩니다.'
              : '승인 요청 뒤의 발주는 내용을 고칠 수 없습니다. 아래 버튼으로 다음 단계를 진행합니다.'}
          </p>
        </div>
        <Link to="/purchase/orders" className="btn btn-secondary">
          목록
        </Link>
      </div>

      {notice && <div className="notice doc-message">{notice}</div>}
      {form.generalError && (
        <div className="doc-message">
          <ErrorAlert error={form.generalError} />
          {form.generalError.code === 'CONCURRENT_MODIFICATION' && po && (
            <button
              type="button"
              className="btn btn-secondary"
              onClick={() => void form.run(() => purchaseOrderApi.get(po.id), '최신 내용을 다시 불러왔습니다.')}
            >
              다시 조회
            </button>
          )}
        </div>
      )}

      <div className="doc-layout">
        {/* noValidate: 검증의 기준은 서버다. 화면은 서버가 돌려준 오류를 해당 칸에 보여 준다 */}
        <form className="doc-main" onSubmit={handleSave} noValidate>
          <section className="panel doc-fields">
            <Field label="공급처" error={form.fieldError('supplierId')}>
              {editable ? (
                <select value={values.supplierId} onChange={(e) => set('supplierId', e.target.value)}>
                  <option value="">선택</option>
                  {supplierOptions.map((partner) => (
                    <option key={partner.id} value={partner.id}>
                      {partner.code} {partner.name}
                    </option>
                  ))}
                </select>
              ) : (
                <span className="readonly">
                  {po?.supplier.code} {po?.supplier.name}
                </span>
              )}
            </Field>
            <Field label="입고 창고" error={form.fieldError('warehouseId')}>
              {editable ? (
                <select value={values.warehouseId} onChange={(e) => set('warehouseId', e.target.value)}>
                  <option value="">선택</option>
                  {warehouseOptions.map((warehouse) => (
                    <option key={warehouse.id} value={warehouse.id}>
                      {warehouse.code} {warehouse.name}
                    </option>
                  ))}
                </select>
              ) : (
                <span className="readonly">{po?.warehouse.name}</span>
              )}
            </Field>
            <Field label="발주일" error={form.fieldError('orderDate')}>
              {editable ? (
                <input type="date" value={values.orderDate} onChange={(e) => set('orderDate', e.target.value)} />
              ) : (
                <span className="readonly">{po?.orderDate}</span>
              )}
            </Field>
            <Field label="납기 요청일" error={form.fieldError('dueDate')}>
              {editable ? (
                <input type="date" value={values.dueDate} onChange={(e) => set('dueDate', e.target.value)} />
              ) : (
                <span className="readonly">{po?.dueDate ?? '-'}</span>
              )}
            </Field>
            <Field label="메모" error={form.fieldError('memo')}>
              {editable ? (
                <input value={values.memo} onChange={(e) => set('memo', e.target.value)} maxLength={500} />
              ) : (
                <span className="readonly">{po?.memo ?? '-'}</span>
              )}
            </Field>
          </section>

          <section className="panel">
            <div className="panel-title">
              <h2>발주 품목</h2>
              {form.fieldError('lines') && <span className="field-error">{form.fieldError('lines')}</span>}
            </div>
            <div className="table-scroll">
              <table className="grid lines">
                <thead>
                  <tr>
                    <th className="num">#</th>
                    <th>품목</th>
                    <th>규격</th>
                    <th>단위</th>
                    <th className="num">수량</th>
                    <th className="num">단가</th>
                    <th className="num">금액</th>
                    {editable ? (
                      <th aria-label="줄 삭제" />
                    ) : (
                      <>
                        <th className="num">받은 수량</th>
                        <th className="num">남은 수량</th>
                      </>
                    )}
                  </tr>
                </thead>
                <tbody>
                  {editable
                    ? values.lines.map((line, index) => {
                        const item = itemById.get(Number(line.itemId))
                        const quantity = toNumber(line.quantity)
                        const price = toNumber(line.unitPrice)
                        const itemError = form.fieldError(`lines[${index}].itemId`)
                        const quantityError = form.fieldError(`lines[${index}].quantity`)
                        const priceError = form.fieldError(`lines[${index}].unitPrice`)
                        return (
                          <tr key={line.key}>
                            <td className="num">{index + 1}</td>
                            <td className={itemError ? 'field-invalid' : undefined}>
                              <select
                                aria-label={`${index + 1}번 줄 품목`}
                                value={line.itemId}
                                onChange={(e) => setLine(line.key, { itemId: e.target.value })}
                              >
                                <option value="">선택</option>
                                {itemOptions.map((option) => (
                                  <option key={option.id} value={option.id}>
                                    {option.code} {option.name}
                                  </option>
                                ))}
                              </select>
                              {itemError && <div className="field-error">{itemError}</div>}
                            </td>
                            <td>{item && 'spec' in item ? (item.spec ?? '-') : '-'}</td>
                            <td>{item && 'unit' in item ? item.unit : '-'}</td>
                            <td className={quantityError ? 'num field-invalid' : 'num'}>
                              <input
                                aria-label={`${index + 1}번 줄 수량`}
                                className="num-input"
                                inputMode="decimal"
                                value={line.quantity}
                                onChange={(e) => setLine(line.key, { quantity: e.target.value })}
                              />
                              {quantityError && <div className="field-error">{quantityError}</div>}
                            </td>
                            <td className={priceError ? 'num field-invalid' : 'num'}>
                              <input
                                aria-label={`${index + 1}번 줄 단가`}
                                className="num-input"
                                inputMode="decimal"
                                value={line.unitPrice}
                                onChange={(e) => setLine(line.key, { unitPrice: e.target.value })}
                              />
                              {priceError && <div className="field-error">{priceError}</div>}
                            </td>
                            <td className="num">
                              {quantity !== null && price !== null ? formatMoney(Math.round(quantity * price)) : '-'}
                            </td>
                            <td>
                              <button
                                type="button"
                                className="btn-icon"
                                aria-label={`${index + 1}번 줄 삭제`}
                                disabled={values.lines.length === 1}
                                onClick={() =>
                                  set(
                                    'lines',
                                    values.lines.filter((l) => l.key !== line.key),
                                  )
                                }
                              >
                                ×
                              </button>
                            </td>
                          </tr>
                        )
                      })
                    : po?.lines.map((line) => (
                        <tr key={line.id}>
                          <td className="num">{line.lineNo}</td>
                          <td>
                            <code>{line.item.code}</code> {line.item.name}
                          </td>
                          <td>{line.item.spec ?? '-'}</td>
                          <td>{line.item.unit}</td>
                          <td className="num">{formatQuantity(line.quantity)}</td>
                          <td className="num">{formatMoney(line.unitPrice)}</td>
                          <td className="num">{formatMoney(line.amount)}</td>
                          <td className="num">{formatQuantity(line.receivedQuantity)}</td>
                          <td className={line.remainingQuantity > 0 ? 'num remaining' : 'num'}>
                            {formatQuantity(line.remainingQuantity)}
                          </td>
                        </tr>
                      ))}
                </tbody>
              </table>
            </div>
            <div className="lines-footer">
              {editable ? (
                <button type="button" className="btn btn-secondary" onClick={() => set('lines', [...values.lines, newLine()])}>
                  줄 추가
                </button>
              ) : (
                <span />
              )}
              <div className="total">
                합계 <strong>{formatMoney(editable ? estimatedTotal : (po?.totalAmount ?? 0))}</strong>
                {editable && <span className="field-hint"> (저장하면 서버가 다시 계산합니다)</span>}
              </div>
            </div>
          </section>

          <div className="doc-actions">
            {editable && (
              <button type="submit" className="btn" disabled={form.busy}>
                {po ? '저장' : '등록'}
              </button>
            )}
            {po?.status === 'DRAFT' && (
              <button
                type="button"
                className="btn btn-secondary"
                disabled={form.busy || dirty}
                title={dirty ? '고친 내용을 먼저 저장하세요' : undefined}
                onClick={() => act(() => purchaseOrderApi.submit(po), '승인을 요청했습니다.')}
              >
                승인 요청
              </button>
            )}
            {po?.status === 'SUBMITTED' && (
              <>
                <button
                  type="button"
                  className="btn"
                  disabled={form.busy}
                  onClick={() => act(() => purchaseOrderApi.approve(po), '승인했습니다. 이제 입고를 받을 수 있습니다.')}
                >
                  승인
                </button>
                <button
                  type="button"
                  className="btn btn-secondary"
                  disabled={form.busy}
                  onClick={() => actWithReason('반려', (r) => purchaseOrderApi.reject(po, r), '반려했습니다.')}
                >
                  반려
                </button>
              </>
            )}
            {(po?.status === 'APPROVED' || po?.status === 'PARTIALLY_RECEIVED') && (
              <Link to={`/purchase/receipts?order=${po.id}`} className="btn">
                입고 처리
              </Link>
            )}
            {po?.status === 'PARTIALLY_RECEIVED' && (
              <button
                type="button"
                className="btn btn-danger-outline"
                disabled={form.busy}
                onClick={() =>
                  actWithReason('잔량 마감', (r) => purchaseOrderApi.close(po, r), '마감했습니다. 남은 수량은 더 받지 않습니다.')
                }
              >
                잔량 마감
              </button>
            )}
            {(po?.status === 'DRAFT' || po?.status === 'SUBMITTED' || po?.status === 'APPROVED') && (
              <button
                type="button"
                className="btn btn-danger-outline"
                disabled={form.busy}
                onClick={() => actWithReason('취소', (r) => purchaseOrderApi.cancel(po, r), '취소했습니다.')}
              >
                발주 취소
              </button>
            )}
          </div>
          {form.fieldError('reason') && <div className="field-error">{form.fieldError('reason')}</div>}
          {dirty && po?.status === 'DRAFT' && (
            <div className="field-hint">고친 내용이 있습니다. 저장한 뒤 승인을 요청하세요.</div>
          )}
          {po?.status === 'SUBMITTED' && selfApproval && (
            <div className="field-hint">
              지금 작업자({operator})는 이 발주를 작성했거나 승인 요청했습니다. 승인하면 서버가 거절합니다(직무 분리).
              상단에서 작업자를 바꿔 보세요.
            </div>
          )}
        </form>

        {po && (
          <aside className="doc-side">
            <section className="panel">
              <h2>진행 이력</h2>
              <EventTimeline events={po.events} />
            </section>
            <section className="panel">
              <h2>입고 내역</h2>
              {receipts.length === 0 ? (
                <p className="field-hint">아직 입고가 없습니다.</p>
              ) : (
                <ul className="receipt-list">
                  {receipts.map((receipt) => (
                    <li key={receipt.id}>
                      <code>{receipt.receiptNo}</code> {receipt.receiptDate} · {receipt.createdBy}
                      <div className="field-hint">
                        {receipt.lines.map((line) => `${line.item.name} ${formatQuantity(line.quantity)}`).join(', ')}
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </section>
            <dl className="audit panel">
              <dt>등록</dt>
              <dd>
                {po.createdBy} · {formatDateTime(po.createdAt)}
              </dd>
              <dt>수정</dt>
              <dd>
                {po.updatedBy} · {formatDateTime(po.updatedAt)}
              </dd>
            </dl>
          </aside>
        )}
      </div>
    </section>
  )
}
