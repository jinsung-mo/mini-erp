import { useEffect, useState } from 'react'
import { type Warehouse, warehouseApi } from '../../api/warehouses'

/** 창고 선택지. 사용 중지된 창고도 재고 조회에는 나와야 하므로 활성 여부로 거르지 않는다 */
export function useWarehouses(): Warehouse[] {
  const [warehouses, setWarehouses] = useState<Warehouse[]>([])
  useEffect(() => {
    let ignore = false
    warehouseApi.search({}).then(
      (result) => !ignore && setWarehouses(result),
      () => undefined,
    )
    return () => {
      ignore = true
    }
  }, [])
  return warehouses
}
