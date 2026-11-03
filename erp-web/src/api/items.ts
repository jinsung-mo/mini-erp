import { api } from './client'

/** 서버 Unit enum과 같은 값. 바꾸면 서버 enum과 DB CHECK 제약도 함께 바꿔야 한다 */
export type Unit = 'EA' | 'BOX' | 'KG' | 'L'

export const UNITS: { value: Unit; label: string }[] = [
  { value: 'EA', label: 'EA (개)' },
  { value: 'BOX', label: 'BOX (박스)' },
  { value: 'KG', label: 'KG (킬로그램)' },
  { value: 'L', label: 'L (리터)' },
]

export type Item = {
  id: number
  code: string
  name: string
  spec: string | null
  unit: Unit
  active: boolean
  createdAt: string
  createdBy: string
  updatedAt: string
  updatedBy: string
}

/** 수정할 수 있는 값. 코드는 등록할 때만 보낸다 */
export type ItemInput = { name: string; spec: string; unit: Unit }

export type ItemSearch = { keyword?: string; active?: boolean }

export const itemApi = {
  search(params: ItemSearch): Promise<Item[]> {
    const query = new URLSearchParams()
    if (params.keyword) query.set('keyword', params.keyword)
    if (params.active !== undefined) query.set('active', String(params.active))
    const qs = query.toString()
    return api<Item[]>(`/items${qs ? `?${qs}` : ''}`)
  },

  create(input: ItemInput & { code: string }): Promise<Item> {
    return api<Item>('/items', { method: 'POST', body: JSON.stringify(input) })
  },

  update(id: number, input: ItemInput): Promise<Item> {
    return api<Item>(`/items/${id}`, { method: 'PUT', body: JSON.stringify(input) })
  },

  deactivate(id: number): Promise<Item> {
    return api<Item>(`/items/${id}/deactivate`, { method: 'POST' })
  },

  activate(id: number): Promise<Item> {
    return api<Item>(`/items/${id}/activate`, { method: 'POST' })
  },
}
