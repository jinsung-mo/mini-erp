import { api, queryString } from './client'
import type { ItemRef, Ref } from './purchaseOrders'

export type Stock = {
  id: number
  item: ItemRef
  warehouse: Ref
  quantity: number
  /** 확정한 수주 중 아직 내보내지 않은 수량 */
  allocatedQuantity: number
  /** 현재고 - 할당. 새 수주에 약속할 수 있는 수량. 실사 분실이 있으면 음수일 수 있다 */
  availableQuantity: number
  updatedAt: string
  updatedBy: string
}

export type StockTransactionType = 'GOODS_RECEIPT' | 'ADJUSTMENT' | 'SHIPMENT'

export const TRANSACTION_TYPES: { value: StockTransactionType; label: string }[] = [
  { value: 'GOODS_RECEIPT', label: '입고' },
  { value: 'SHIPMENT', label: '출고' },
  { value: 'ADJUSTMENT', label: '재고 조정' },
]

export type StockTransaction = {
  id: number
  type: StockTransactionType
  typeLabel: string
  item: ItemRef
  warehouse: Ref
  /** 들어오면 +, 나가면 - */
  quantity: number
  /** 이 거래 직후의 현재고 */
  balanceAfter: number
  txnDate: string
  sourceNo: string
  reason: string | null
  occurredAt: string
  actor: string
}

export type StockSearch = { keyword?: string; warehouseId?: number; includeZero?: boolean }

export type TransactionSearch = {
  keyword?: string
  itemId?: number
  warehouseId?: number
  type?: StockTransactionType
  fromDate?: string
  toDate?: string
}

export type AdjustmentInput = {
  itemId: number
  warehouseId: number
  /** 화면이 본 장부 수량. 그사이 재고가 바뀌었으면 서버가 409로 거절한다 */
  bookQuantity: number
  countedQuantity: number | null
  adjustDate: string
  reason: string
}

export const inventoryApi = {
  stocks({ keyword, warehouseId, includeZero }: StockSearch): Promise<Stock[]> {
    return api<Stock[]>(
      `/stocks${queryString({ keyword, warehouseId: warehouseId?.toString(), includeZero: includeZero ? 'true' : undefined })}`,
    )
  },

  transactions({ keyword, itemId, warehouseId, type, fromDate, toDate }: TransactionSearch): Promise<StockTransaction[]> {
    return api<StockTransaction[]>(
      `/stock-transactions${queryString({
        keyword,
        itemId: itemId?.toString(),
        warehouseId: warehouseId?.toString(),
        type,
        fromDate,
        toDate,
      })}`,
    )
  },

  adjust(input: AdjustmentInput): Promise<StockTransaction> {
    return api<StockTransaction>('/stock-adjustments', { method: 'POST', body: JSON.stringify(input) })
  },
}
