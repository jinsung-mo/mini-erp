import { type FormEvent, useEffect, useRef, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { toApiError, type ApiError } from '../../api/client'
import { inventoryApi } from '../../api/inventory'
import { type Item, itemApi } from '../../api/items'
import { type Partner, partnerApi } from '../../api/partners'
import type { Ref } from '../../api/purchaseOrders'
import { type SalesOrder, type SalesOrderInput, salesOrderApi } from '../../api/salesOrders'
import { type Shipment, shipmentApi } from '../../api/shipments'
import { type Warehouse, warehouseApi } from '../../api/warehouses'
import { Field } from '../../components/Field'
import { formatDateTime, formatMoney, formatQuantity, today } from '../../format'
import { ErrorAlert } from '../master/MasterParts'
import { useMasterForm } from '../master/useMasterForm'
import { SoEventTimeline, SoStatusTag } from './SalesParts'

/** 화면 입력 상태. 입력 칸은 문자열로 들고 있다가 보낼 때 숫자로 바꾼다 (빈 칸과 0을 구분해야 한다) */
type LineValues = { key: number; itemId: string; quantity: string; unitPrice: string }

type Values = {
  customerId: string
  warehouseId: string
  orderDate: string
  dueDate: string
  memo: string
  lines: LineValues[]
}

type Options = { customers: Partner[]; warehouses: Warehouse[]; items: Item[] }

let lineKey = 0

function newLine(): LineValues {
  lineKey += 1
  return { key: lineKey, itemId: '', quantity: '', unitPrice: '' }
}

function emptyValues(): Values {
  return { customerId: '', warehouseId: '', orderDate: today(), dueDate: '', memo: '', lines: [newLine()] }
}

function fromSo(so: SalesOrder): Values {
  return {
    customerId: String(so.customer.id),
    warehouseId: String(so.warehouse.id),
    orderDate: so.orderDate,
    dueDate: so.dueDate ?? '',
    memo: so.memo ?? '',
    lines: so.lines.map((line) => ({
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

function toInput(values: Values): SalesOrderInput {
  return {
    customerId: toNumber(values.customerId),
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

/** 사용 중지된 기준정보는 선택 목록에 없지만, 이미 저장된 수주가 가리키고 있으면 보여 줘야 한다 */
function withCurrent<T extends { id: number }>(options: T[], current: (Ref & Partial<T>)[]): (T | Ref)[] {
  const missing = current.filter((ref) => !options.some((option) => option.id === ref.id))
  return [...options, ...missing]
}

/** 라우트: /sales/orders/new, /sales/orders/:id. 수주가 바뀌면 화면 상태를 새로 시작한다 */
export function SalesOrderPage() {
  const { id } = useParams()
  const orderId = id === undefined || id === 'new' ? null : Number(id)
  return <SalesOrderDocument key={id} orderId={orderId} />
}

function SalesOrderDocument({ orderId }: { orderId: number | null }) {
  const navigate = useNavigate()
  const location = useLocation()
  const [so, setSo] = useState<SalesOrder | null>(null)
  const [values, setValues] = useState<Values>(emptyValues)
  const [loading, setLoading] = useState(orderId !== null)
  const [loadError, setLoadError] = useState<ApiError | null>(null)
  const [notice, setNotice] = useState<string | null>(
    (location.state as { notice?: string } | null)?.notice ?? null,
  )
  const [shipments, setShipments] = useState<Shipment[]>([])
  const [options, setOptions] = useState<Options>({ customers: [], warehouses: [], items: [] })
  const shipmentSeq = useRef(0)

  const form = useMasterForm<SalesOrder>(saved, { NOT_A_CUSTOMER: 'customerId' })

  function saved(next: SalesOrder, message: string) {
    if (orderId === null) {
      // 등록하면 주소를 수주 번호의 주소로 바꾼다 (새로고침해도 같은 수주가 보이게)
      navigate(`/sales/orders/${next.id}`, { replace: true, state: { notice: message } })
      return
    }
    setSo(next)
    setValues(fromSo(next))
    setNotice(message)
    loadShipments(next.id)
  }

  function loadShipments(id: number) {
    const seq = ++shipmentSeq.current
    shipmentApi.search({ salesOrderId: id }).then(
      (result) => seq === shipmentSeq.current && setShipments(result),
      () => seq === shipmentSeq.current && setShipments([]),
    )
  }

  useEffect(() => {
    if (orderId === null) return
    let ignore = false
    salesOrderApi.get(orderId).then(
      (result) => {
        if (ignore) return
        setSo(result)
        setValues(fromSo(result))
        setLoading(false)
      },
      (e: unknown) => {
        if (ignore) return
        setLoadError(toApiError(e))
        setLoading(false)
      },
    )
    loadShipments(orderId)
    return () => {
      ignore = true
    }
  }, [orderId])

  const editable = so === null || so.status === 'DRAFT'

  // 작성 중일 때만 선택 목록이 필요하다. 사용 중인 기준정보만, 거래처는 고객만 고른다
  useEffect(() => {
    if (!editable) return
    let ignore = false
    Promise.all([
      partnerApi.search({ role: 'CUSTOMER', active: true }),
      warehouseApi.search({ active: true }),
      itemApi.search({ active: true }),
    ]).then(
      ([customers, warehouses, items]) => !ignore && setOptions({ customers, warehouses, items }),
      () => {
        // 선택 목록을 못 불러와도 저장할 때 서버가 알려 준다
      },
    )
    return () => {
      ignore = true
    }
  }, [editable])

  // 작성 중에 고른 출고 창고의 가용 재고 (품목 id → 가용). 참고용이고, 확정할 때 서버가 잠근 뒤 다시 확인한다
  const [available, setAvailable] = useState<Map<number, number> | null>(null)
  const availabilityWarehouse = editable ? values.warehouseId : ''
  useEffect(() => {
    if (!availabilityWarehouse) return
    let ignore = false
    inventoryApi.stocks({ warehouseId: Number(availabilityWarehouse), includeZero: true }).then(
      (stocks) => !ignore && setAvailable(new Map(stocks.map((stock) => [stock.item.id, stock.availableQuantity]))),
      () => !ignore && setAvailable(null),
    )
    return () => {
      ignore = true
    }
  }, [availabilityWarehouse])

  if (loading) return <p className="page-desc">불러오는 중…</p>
  if (loadError) {
    return (
      <section className="page">
        <ErrorAlert error={loadError} />
        <Link to="/sales/orders">수주 목록으로</Link>
      </section>
    )
  }

  const dirty = so !== null && JSON.stringify(toInput(values)) !== JSON.stringify(toInput(fromSo(so)))

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
    if (so === null) {
      void form.run(() => salesOrderApi.create(input), '등록했습니다. 내용을 확인하고 확정하세요.')
    } else {
      void form.run(() => salesOrderApi.update(so.id, so.version, input), '저장했습니다.')
    }
  }

  function act(action: () => Promise<SalesOrder>, message: string) {
    setNotice(null)
    void form.run(action, message)
  }

  /** 취소·마감은 사유가 필요하다. 빈 사유는 서버가 거절하고 오류를 보여 준다 */
  function actWithReason(label: string, action: (reason: string) => Promise<SalesOrder>, message: string) {
    const reason = window.prompt(`${label} 사유를 입력하세요.`)
    if (reason === null) return
    act(() => action(reason), message)
  }

  const itemOptions = withCurrent(options.items, so?.lines.map((line) => line.item) ?? [])
  const customerOptions = withCurrent(options.customers, so ? [so.customer] : [])
  const warehouseOptions = withCurrent(options.warehouses, so ? [so.warehouse] : [])
  const itemById = new Map(itemOptions.map((item) => [item.id, item]))
  /** 창고를 고르지 않았으면 null. 현재고 줄이 없는 품목은 가용 0 */
  const availableOf = (itemId: number): number | null =>
    availabilityWarehouse && available ? (available.get(itemId) ?? 0) : null


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
            {so ? '수주서' : '신규 수주'}
            {so && <code className="form-code">{so.orderNo}</code>}
            {so && <SoStatusTag status={so.status} />}
          </h1>
          <p className="page-desc">
            {editable
              ? '작성 중인 수주는 자유롭게 고칠 수 있습니다. 확정하면 서버가 가용 재고를 확인하고 할당하며, 그 뒤에는 고칠 수 없습니다.'
              : '확정한 수주는 재고를 약속한 상태라 고칠 수 없습니다. 바꾸려면 취소하고 새로 만듭니다.'}
          </p>
        </div>
        <Link to="/sales/orders" className="btn btn-secondary">
          목록
        </Link>
      </div>

      {notice && <div className="notice doc-message">{notice}</div>}
      {form.generalError && (
        <div className="doc-message">
          <ErrorAlert error={form.generalError} />
          {form.generalError.code === 'CONCURRENT_MODIFICATION' && so && (
            <button
              type="button"
              className="btn btn-secondary"
              onClick={() => void form.run(() => salesOrderApi.get(so.id), '최신 내용을 다시 불러왔습니다.')}
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
            <Field label="고객" error={form.fieldError('customerId')}>
              {editable ? (
                <select value={values.customerId} onChange={(e) => set('customerId', e.target.value)}>
                  <option value="">선택</option>
                  {customerOptions.map((partner) => (
                    <option key={partner.id} value={partner.id}>
                      {partner.code} {partner.name}
                    </option>
                  ))}
                </select>
              ) : (
                <span className="readonly">
                  {so?.customer.code} {so?.customer.name}
                </span>
              )}
            </Field>
            <Field label="출고 창고" error={form.fieldError('warehouseId')}>
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
                <span className="readonly">{so?.warehouse.name}</span>
              )}
            </Field>
            <Field label="수주일" error={form.fieldError('orderDate')}>
              {editable ? (
                <input type="date" value={values.orderDate} onChange={(e) => set('orderDate', e.target.value)} />
              ) : (
                <span className="readonly">{so?.orderDate}</span>
              )}
            </Field>
            <Field label="납기 요청일" error={form.fieldError('dueDate')}>
              {editable ? (
                <input type="date" value={values.dueDate} onChange={(e) => set('dueDate', e.target.value)} />
              ) : (
                <span className="readonly">{so?.dueDate ?? '-'}</span>
              )}
            </Field>
            <Field label="메모" error={form.fieldError('memo')}>
              {editable ? (
                <input value={values.memo} onChange={(e) => set('memo', e.target.value)} maxLength={500} />
              ) : (
                <span className="readonly">{so?.memo ?? '-'}</span>
              )}
            </Field>
          </section>

          <section className="panel">
            <div className="panel-title">
              <h2>수주 품목</h2>
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
                      <>
                        <th className="num" title="출고 창고의 현재고 - 이미 약속한 수량. 확정할 때 서버가 다시 확인합니다">
                          가용
                        </th>
                        <th aria-label="줄 삭제" />
                      </>
                    ) : (
                      <>
                        <th className="num">출고한 수량</th>
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
                        const lineAvailable = item ? availableOf(item.id) : null
                        const short = lineAvailable !== null && quantity !== null && quantity > lineAvailable
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
                            <td className={short ? 'num short' : 'num'} title={short ? '확정하면 재고 부족으로 거절됩니다' : undefined}>
                              {lineAvailable === null ? '-' : formatQuantity(lineAvailable)}
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
                    : so?.lines.map((line) => (
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
                          <td className="num">{formatQuantity(line.shippedQuantity)}</td>
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
                합계 <strong>{formatMoney(editable ? estimatedTotal : (so?.totalAmount ?? 0))}</strong>
                {editable && <span className="field-hint"> (저장하면 서버가 다시 계산합니다)</span>}
              </div>
            </div>
          </section>

          <div className="doc-actions">
            {editable && (
              <button type="submit" className="btn" disabled={form.busy}>
                {so ? '저장' : '등록'}
              </button>
            )}
            {so?.status === 'DRAFT' && (
              <button
                type="button"
                className="btn btn-secondary"
                disabled={form.busy || dirty}
                title={dirty ? '고친 내용을 먼저 저장하세요' : undefined}
                onClick={() => act(() => salesOrderApi.confirm(so), '확정했습니다. 재고를 할당했고 이제 출고할 수 있습니다.')}
              >
                확정 (재고 할당)
              </button>
            )}
            {(so?.status === 'CONFIRMED' || so?.status === 'PARTIALLY_SHIPPED') && (
              <Link to={`/sales/shipments?order=${so.id}`} className="btn">
                출고 처리
              </Link>
            )}
            {so?.status === 'PARTIALLY_SHIPPED' && (
              <button
                type="button"
                className="btn btn-danger-outline"
                disabled={form.busy}
                onClick={() =>
                  actWithReason(
                    '잔량 마감',
                    (r) => salesOrderApi.close(so, r),
                    '마감했습니다. 남은 수량의 할당을 풀었습니다.',
                  )
                }
              >
                잔량 마감
              </button>
            )}
            {(so?.status === 'DRAFT' || so?.status === 'CONFIRMED') && (
              <button
                type="button"
                className="btn btn-danger-outline"
                disabled={form.busy}
                onClick={() =>
                  actWithReason(
                    '취소',
                    (r) => salesOrderApi.cancel(so, r),
                    so.status === 'CONFIRMED' ? '취소했습니다. 할당한 재고를 풀었습니다.' : '취소했습니다.',
                  )
                }
              >
                수주 취소
              </button>
            )}
          </div>
          {form.fieldError('reason') && <div className="field-error">{form.fieldError('reason')}</div>}
          {dirty && so?.status === 'DRAFT' && (
            <div className="field-hint">고친 내용이 있습니다. 저장한 뒤 확정하세요.</div>
          )}
        </form>

        {so && (
          <aside className="doc-side">
            <section className="panel">
              <h2>진행 이력</h2>
              <SoEventTimeline events={so.events} />
            </section>
            <section className="panel">
              <h2>출고 내역</h2>
              {shipments.length === 0 ? (
                <p className="field-hint">아직 출고가 없습니다.</p>
              ) : (
                <ul className="receipt-list">
                  {shipments.map((shipment) => (
                    <li key={shipment.id}>
                      <code>{shipment.shipmentNo}</code> {shipment.shipDate} · {shipment.createdBy}
                      <div className="field-hint">
                        {shipment.lines.map((line) => `${line.item.name} ${formatQuantity(line.quantity)}`).join(', ')}
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </section>
            <dl className="audit panel">
              <dt>등록</dt>
              <dd>
                {so.createdBy} · {formatDateTime(so.createdAt)}
              </dd>
              <dt>수정</dt>
              <dd>
                {so.updatedBy} · {formatDateTime(so.updatedAt)}
              </dd>
            </dl>
          </aside>
        )}
      </div>
    </section>
  )
}
