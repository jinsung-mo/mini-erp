import { api, queryString } from './client'
import type { ItemRef, Ref } from './purchaseOrders'

/** 서버 SalesOrderStatus와 같은 값. 할당이 걸려 있는 상태는 CONFIRMED, PARTIALLY_SHIPPED 둘뿐이다 */
export type SalesOrderStatus = 'DRAFT' | 'CONFIRMED' | 'PARTIALLY_SHIPPED' | 'SHIPPED' | 'CLOSED' | 'CANCELED'

export const SO_STATUSES: { value: SalesOrderStatus; label: string }[] = [
  { value: 'DRAFT', label: '작성 중' },
  { value: 'CONFIRMED', label: '확정' },
  { value: 'PARTIALLY_SHIPPED', label: '부분 출고' },
  { value: 'SHIPPED', label: '출고 완료' },
  { value: 'CLOSED', label: '마감' },
  { value: 'CANCELED', label: '취소' },
]

export const OPEN_SO_STATUSES: SalesOrderStatus[] = ['DRAFT', 'CONFIRMED', 'PARTIALLY_SHIPPED']

export function soStatusLabel(status: SalesOrderStatus): string {
  return SO_STATUSES.find((s) => s.value === status)?.label ?? status
}

export type SoEventType = 'CREATED' | 'CONFIRMED' | 'SHIPPED' | 'CLOSED' | 'CANCELED'

export const SO_EVENT_LABELS: Record<SoEventType, string> = {
  CREATED: '작성',
  CONFIRMED: '확정 (재고 할당)',
  SHIPPED: '출고',
  CLOSED: '마감',
  CANCELED: '취소',
}

export type SalesOrderSummary = {
  id: number
  orderNo: string
  status: SalesOrderStatus
  customer: Ref
  warehouse: Ref
  orderDate: string
  dueDate: string | null
  lineCount: number
  totalAmount: number
  version: number
  updatedAt: string
  updatedBy: string
}

export type SalesOrderLine = {
  id: number
  lineNo: number
  item: ItemRef
  quantity: number
  unitPrice: number
  amount: number
  shippedQuantity: number
  /** 확정 뒤에는 이 줄이 붙잡고 있는 할당량이기도 하다 */
  remainingQuantity: number
}

export type SalesOrderEvent = {
  type: SoEventType
  fromStatus: SalesOrderStatus | null
  toStatus: SalesOrderStatus
  reason: string | null
  occurredAt: string
  actor: string
}

export type SalesOrder = {
  id: number
  orderNo: string
  status: SalesOrderStatus
  customer: Ref
  warehouse: Ref
  orderDate: string
  dueDate: string | null
  memo: string | null
  totalAmount: number
  lines: SalesOrderLine[]
  events: SalesOrderEvent[]
  version: number
  createdAt: string
  createdBy: string
  updatedAt: string
  updatedBy: string
}

export type SalesLineInput = { itemId: number | null; quantity: number | null; unitPrice: number | null }

export type SalesOrderInput = {
  customerId: number | null
  warehouseId: number | null
  orderDate: string
  dueDate: string | null
  memo: string
  lines: SalesLineInput[]
}

export type SalesOrderSearch = { keyword?: string; status?: SalesOrderStatus[] }

function searchQuery({ keyword, status }: SalesOrderSearch): string {
  const base = queryString({ keyword })
  if (!status || status.length === 0) return base
  const multi = status.map((s) => `status=${s}`).join('&')
  return base ? `${base}&${multi}` : `?${multi}`
}

function post<T>(path: string, body: unknown): Promise<T> {
  return api<T>(path, { method: 'POST', body: JSON.stringify(body) })
}

export const salesOrderApi = {
  search(params: SalesOrderSearch): Promise<SalesOrderSummary[]> {
    return api<SalesOrderSummary[]>(`/sales-orders${searchQuery(params)}`)
  },

  get(id: number): Promise<SalesOrder> {
    return api<SalesOrder>(`/sales-orders/${id}`)
  },

  create(input: SalesOrderInput): Promise<SalesOrder> {
    return post<SalesOrder>('/sales-orders', input)
  },

  update(id: number, version: number, input: SalesOrderInput): Promise<SalesOrder> {
    return api<SalesOrder>(`/sales-orders/${id}`, { method: 'PUT', body: JSON.stringify({ ...input, version }) })
  },

  /** 확정: 서버가 가용 재고를 확인하고 할당한다. 모자라면 409 INSUFFICIENT_STOCK */
  confirm(so: SalesOrder): Promise<SalesOrder> {
    return post<SalesOrder>(`/sales-orders/${so.id}/confirm`, { version: so.version })
  },

  cancel(so: SalesOrder, reason: string): Promise<SalesOrder> {
    return post<SalesOrder>(`/sales-orders/${so.id}/cancel`, { version: so.version, reason })
  },

  close(so: SalesOrder, reason: string): Promise<SalesOrder> {
    return post<SalesOrder>(`/sales-orders/${so.id}/close`, { version: so.version, reason })
  },
}
