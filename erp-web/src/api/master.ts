/** 기준정보(품목·거래처·창고)의 공통 필드: 업무 키(code), 사용 여부, 감사 정보 */
export type MasterRecord = {
  id: number
  code: string
  active: boolean
  createdAt: string
  createdBy: string
  updatedAt: string
  updatedBy: string
}
