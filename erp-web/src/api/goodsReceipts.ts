import { api, queryString } from './client'
import type { ItemRef, Ref } from './purchaseOrders'

export type GoodsReceiptLine = {
  id: number
  purchaseOrderLineId: number
  lineNo: number
  item: ItemRef
  quantity: number
}

export type GoodsReceipt = {
  id: number
  receiptNo: string
  purchaseOrder: { id: number; orderNo: string }
  supplier: Ref
  warehouse: Ref
  receiptDate: string
  memo: string | null
  lines: GoodsReceiptLine[]
  createdAt: string
  createdBy: string
}

export type ReceiptInput = {
  receiptDate: string
  memo: string
  lines: { purchaseOrderLineId: number; quantity: number | null }[]
}

export type GoodsReceiptSearch = { keyword?: string; purchaseOrderId?: number }

export const goodsReceiptApi = {
  search({ keyword, purchaseOrderId }: GoodsReceiptSearch): Promise<GoodsReceipt[]> {
    return api<GoodsReceipt[]>(
      `/goods-receipts${queryString({ keyword, purchaseOrderId: purchaseOrderId?.toString() })}`,
    )
  },

  /** 입고는 version을 보내지 않는다. 서버가 발주를 잠그고 남은 수량을 다시 확인한다 */
  receive(purchaseOrderId: number, input: ReceiptInput): Promise<GoodsReceipt> {
    return api<GoodsReceipt>(`/purchase-orders/${purchaseOrderId}/receipts`, {
      method: 'POST',
      body: JSON.stringify(input),
    })
  },
}
