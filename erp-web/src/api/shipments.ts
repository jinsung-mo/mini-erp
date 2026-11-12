import { api, queryString } from './client'
import type { ItemRef, Ref } from './purchaseOrders'

export type ShipmentLine = {
  id: number
  salesOrderLineId: number
  lineNo: number
  item: ItemRef
  quantity: number
}

export type Shipment = {
  id: number
  shipmentNo: string
  salesOrder: { id: number; orderNo: string }
  customer: Ref
  warehouse: Ref
  shipDate: string
  memo: string | null
  lines: ShipmentLine[]
  createdAt: string
  createdBy: string
}

export type ShipmentInput = {
  shipDate: string
  memo: string
  lines: { salesOrderLineId: number; quantity: number | null }[]
}

export type ShipmentSearch = { keyword?: string; salesOrderId?: number }

export const shipmentApi = {
  search({ keyword, salesOrderId }: ShipmentSearch): Promise<Shipment[]> {
    return api<Shipment[]>(`/shipments${queryString({ keyword, salesOrderId: salesOrderId?.toString() })}`)
  },

  /** 출고는 version을 보내지 않는다. 서버가 수주를 잠그고 남은 수량과 실제 재고를 다시 확인한다 */
  ship(salesOrderId: number, input: ShipmentInput): Promise<Shipment> {
    return api<Shipment>(`/sales-orders/${salesOrderId}/shipments`, { method: 'POST', body: JSON.stringify(input) })
  },
}
