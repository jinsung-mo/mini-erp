import { api, queryString } from './client'
import type { MasterRecord } from './master'

/** 서버 PartnerRole enum과 같은 값. 한 거래처가 둘 다 가질 수 있다 */
export type PartnerRole = 'SUPPLIER' | 'CUSTOMER'

export const PARTNER_ROLES: { value: PartnerRole; label: string; description: string }[] = [
  { value: 'SUPPLIER', label: '공급처', description: '매입: 물건을 사 오는 곳' },
  { value: 'CUSTOMER', label: '고객', description: '매출: 물건을 파는 곳' },
]

export type Partner = MasterRecord & {
  name: string
  /** 숫자 10자리 (하이픈 없음). 화면에 보일 때만 formatBusinessNo로 하이픈을 붙인다 */
  businessNo: string | null
  roles: PartnerRole[]
}

/** 사업자등록번호는 하이픈이 있어도 된다. 서버가 숫자만 남겨 저장한다 */
export type PartnerInput = { name: string; businessNo: string; roles: PartnerRole[] }

export type PartnerSearch = { keyword?: string; role?: PartnerRole; active?: boolean }

export const partnerApi = {
  /** 2단계 발주 화면은 { role: 'SUPPLIER', active: true }로 공급처만 고른다 */
  search(params: PartnerSearch): Promise<Partner[]> {
    return api<Partner[]>(`/partners${queryString(params)}`)
  },

  create(input: PartnerInput & { code: string }): Promise<Partner> {
    return api<Partner>('/partners', { method: 'POST', body: JSON.stringify(input) })
  },

  update(id: number, input: PartnerInput): Promise<Partner> {
    return api<Partner>(`/partners/${id}`, { method: 'PUT', body: JSON.stringify(input) })
  },

  deactivate(id: number): Promise<Partner> {
    return api<Partner>(`/partners/${id}/deactivate`, { method: 'POST' })
  },

  activate(id: number): Promise<Partner> {
    return api<Partner>(`/partners/${id}/activate`, { method: 'POST' })
  },
}
