import { api, queryString } from './client'
import type { MasterRecord } from './master'

export type Warehouse = MasterRecord & {
  name: string
  location: string | null
}

/** 수정할 수 있는 값. 코드는 등록할 때만 보낸다 */
export type WarehouseInput = { name: string; location: string }

export type WarehouseSearch = { keyword?: string; active?: boolean }

export const warehouseApi = {
  search(params: WarehouseSearch): Promise<Warehouse[]> {
    return api<Warehouse[]>(`/warehouses${queryString(params)}`)
  },

  create(input: WarehouseInput & { code: string }): Promise<Warehouse> {
    return api<Warehouse>('/warehouses', { method: 'POST', body: JSON.stringify(input) })
  },

  update(id: number, input: WarehouseInput): Promise<Warehouse> {
    return api<Warehouse>(`/warehouses/${id}`, { method: 'PUT', body: JSON.stringify(input) })
  },

  deactivate(id: number): Promise<Warehouse> {
    return api<Warehouse>(`/warehouses/${id}/deactivate`, { method: 'POST' })
  },

  activate(id: number): Promise<Warehouse> {
    return api<Warehouse>(`/warehouses/${id}/activate`, { method: 'POST' })
  },
}
