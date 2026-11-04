import { type FormEvent, useState } from 'react'
import { type Warehouse, type WarehouseInput, warehouseApi } from '../../api/warehouses'
import { Field } from '../../components/Field'
import { ActivationButton, FormPanel } from './MasterParts'
import { useMasterForm } from './useMasterForm'

type Props = {
  /** null이면 신규 등록 */
  warehouse: Warehouse | null
  notice: string | null
  onSaved: (warehouse: Warehouse, message: string) => void
  onClose: () => void
}

type Values = WarehouseInput & { code: string }

export function WarehouseForm({ warehouse, notice, onSaved, onClose }: Props) {
  const [values, setValues] = useState<Values>({
    code: warehouse?.code ?? '',
    name: warehouse?.name ?? '',
    location: warehouse?.location ?? '',
  })
  const form = useMasterForm(onSaved, { DUPLICATE_CODE: 'code' })

  function set<K extends keyof Values>(key: K, value: Values[K]) {
    setValues((prev) => ({ ...prev, [key]: value }))
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const input: WarehouseInput = { name: values.name, location: values.location }
    if (warehouse === null) {
      void form.run(() => warehouseApi.create({ ...input, code: values.code }), '등록했습니다.')
    } else {
      void form.run(() => warehouseApi.update(warehouse.id, input), '저장했습니다.')
    }
  }

  return (
    <FormPanel
      record={warehouse}
      title={warehouse ? '창고 수정' : '창고 등록'}
      notice={notice}
      error={form.generalError}
      onClose={onClose}
    >
      <form onSubmit={handleSubmit} noValidate>
        <Field
          label="창고 코드"
          error={form.fieldError('code')}
          hint={warehouse ? '코드는 바꿀 수 없습니다.' : '영문 대문자·숫자·하이픈(-) 2~20자. 등록 후 바꿀 수 없습니다.'}
        >
          <input
            value={values.code}
            onChange={(e) => set('code', e.target.value.toUpperCase())}
            disabled={warehouse !== null}
            maxLength={20}
            placeholder="예: WH-MAIN"
            autoFocus={warehouse === null}
          />
        </Field>
        <Field label="창고명" error={form.fieldError('name')}>
          <input value={values.name} onChange={(e) => set('name', e.target.value)} maxLength={100} />
        </Field>
        <Field label="위치" error={form.fieldError('location')}>
          <input
            value={values.location}
            onChange={(e) => set('location', e.target.value)}
            maxLength={200}
            placeholder="예: 본사 1동"
          />
        </Field>

        <div className="form-actions">
          <button type="submit" className="btn" disabled={form.busy}>
            {warehouse ? '저장' : '등록'}
          </button>
          {warehouse && (
            <ActivationButton
              record={warehouse}
              busy={form.busy}
              onDeactivate={() => void form.run(() => warehouseApi.deactivate(warehouse.id), '사용 중지했습니다.')}
              onActivate={() => void form.run(() => warehouseApi.activate(warehouse.id), '다시 사용합니다.')}
            />
          )}
        </div>
      </form>
    </FormPanel>
  )
}
