import { api, queryString } from './client'
import type { Unit } from './items'

/** 서버 PurchaseOrderStatus와 같은 값. 전이 규칙의 기준은 서버이고, 화면은 버튼을 보여 줄지만 정한다 */
export type PurchaseOrderStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'APPROVED'
  | 'PARTIALLY_RECEIVED'
  | 'RECEIVED'
  | 'CLOSED'
  | 'CANCELED'

export const PO_STATUSES: { value: PurchaseOrderStatus; label: string }[] = [
  { value: 'DRAFT', label: '작성 중' },
  { value: 'SUBMITTED', label: '승인 요청' },
  { value: 'APPROVED', label: '승인' },
  { value: 'PARTIALLY_RECEIVED', label: '부분 입고' },
  { value: 'RECEIVED', label: '입고 완료' },
  { value: 'CLOSED', label: '마감' },
  { value: 'CANCELED', label: '취소' },
]

export function statusLabel(status: PurchaseOrderStatus): string {
  return PO_STATUSES.find((s) => s.value === status)?.label ?? status
}

export type EventType = 'CREATED' | 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'RECEIVED' | 'CLOSED' | 'CANCELED'

export const EVENT_LABELS: Record<EventType, string> = {
  CREATED: '작성',
  SUBMITTED: '승인 요청',
  APPROVED: '승인',
  REJECTED: '반려',
  RECEIVED: '입고',
  CLOSED: '마감',
  CANCELED: '취소',
}

export type Ref = { id: number; code: string; name: string }
export type ItemRef = Ref & { spec: string | null; unit: Unit }

export type PurchaseOrderSummary = {
  id: number
  orderNo: string
  status: PurchaseOrderStatus
  supplier: Ref
  warehouse: Ref
  orderDate: string
  dueDate: string | null
  lineCount: number
  totalAmount: number
  version: number
  updatedAt: string
  updatedBy: string
}

export type PurchaseOrderLine = {
  id: number
  lineNo: number
  item: ItemRef
  quantity: number
  unitPrice: number
  amount: number
  receivedQuantity: number
  remainingQuantity: number
}

export type PurchaseOrderEvent = {
  type: EventType
  fromStatus: PurchaseOrderStatus | null
  toStatus: PurchaseOrderStatus
  reason: string | null
  occurredAt: string
  actor: string
}

export type PurchaseOrder = {
  id: number
  orderNo: string
  status: PurchaseOrderStatus
  supplier: Ref
  warehouse: Ref
  orderDate: string
  dueDate: string | null
  memo: string | null
  totalAmount: number
  lines: PurchaseOrderLine[]
  events: PurchaseOrderEvent[]
  /** 화면이 본 버전. 상태를 바꾸는 요청에 그대로 돌려보낸다 (낙관적 락) */
  version: number
  createdAt: string
  createdBy: string
  updatedAt: string
  updatedBy: string
}

/** 수량·단가는 숫자로 보낸다. 비어 있으면 null을 보내서 서버가 "입력하세요" 오류를 돌려주게 한다 */
export type LineInput = { itemId: number | null; quantity: number | null; unitPrice: number | null }

export type PurchaseOrderInput = {
  supplierId: number | null
  warehouseId: number | null
  orderDate: string
  dueDate: string | null
  memo: string
  lines: LineInput[]
}

export type PurchaseOrderSearch = {
  keyword?: string
  status?: PurchaseOrderStatus[]
}

function searchQuery({ keyword, status }: PurchaseOrderSearch): string {
  const base = queryString({ keyword })
  if (!status || status.length === 0) return base
  // 상태는 여러 개를 같은 이름으로 보낸다: ?status=APPROVED&status=PARTIALLY_RECEIVED
  const multi = status.map((s) => `status=${s}`).join('&')
  return base ? `${base}&${multi}` : `?${multi}`
}

function post<T>(path: string, body: unknown): Promise<T> {
  return api<T>(path, { method: 'POST', body: JSON.stringify(body) })
}

export const purchaseOrderApi = {
  search(params: PurchaseOrderSearch): Promise<PurchaseOrderSummary[]> {
    return api<PurchaseOrderSummary[]>(`/purchase-orders${searchQuery(params)}`)
  },

  get(id: number): Promise<PurchaseOrder> {
    return api<PurchaseOrder>(`/purchase-orders/${id}`)
  },

  create(input: PurchaseOrderInput): Promise<PurchaseOrder> {
    return post<PurchaseOrder>('/purchase-orders', input)
  },

  update(id: number, version: number, input: PurchaseOrderInput): Promise<PurchaseOrder> {
    return api<PurchaseOrder>(`/purchase-orders/${id}`, {
      method: 'PUT',
      body: JSON.stringify({ ...input, version }),
    })
  },

  submit(po: PurchaseOrder): Promise<PurchaseOrder> {
    return post<PurchaseOrder>(`/purchase-orders/${po.id}/submit`, { version: po.version })
  },

  approve(po: PurchaseOrder): Promise<PurchaseOrder> {
    return post<PurchaseOrder>(`/purchase-orders/${po.id}/approve`, { version: po.version })
  },

  reject(po: PurchaseOrder, reason: string): Promise<PurchaseOrder> {
    return post<PurchaseOrder>(`/purchase-orders/${po.id}/reject`, { version: po.version, reason })
  },

  cancel(po: PurchaseOrder, reason: string): Promise<PurchaseOrder> {
    return post<PurchaseOrder>(`/purchase-orders/${po.id}/cancel`, { version: po.version, reason })
  },

  close(po: PurchaseOrder, reason: string): Promise<PurchaseOrder> {
    return post<PurchaseOrder>(`/purchase-orders/${po.id}/close`, { version: po.version, reason })
  },
}
