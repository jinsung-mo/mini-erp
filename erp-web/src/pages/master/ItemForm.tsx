import { type FormEvent, useState } from 'react'
import { type Item, type ItemInput, type Unit, itemApi, UNITS } from '../../api/items'
import { Field } from '../../components/Field'
import { ActivationButton, FormPanel } from './MasterParts'
import { useMasterForm } from './useMasterForm'

type Props = {
  /** null이면 신규 등록 */
  item: Item | null
  notice: string | null
  onSaved: (item: Item, message: string) => void
  onClose: () => void
}

type Values = ItemInput & { code: string }

export function ItemForm({ item, notice, onSaved, onClose }: Props) {
  const [values, setValues] = useState<Values>({
    code: item?.code ?? '',
    name: item?.name ?? '',
    spec: item?.spec ?? '',
    unit: item?.unit ?? 'EA',
  })
  const form = useMasterForm(onSaved, { DUPLICATE_CODE: 'code' })

  function set<K extends keyof Values>(key: K, value: Values[K]) {
    setValues((prev) => ({ ...prev, [key]: value }))
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const input: ItemInput = { name: values.name, spec: values.spec, unit: values.unit }
    if (item === null) {
      void form.run(() => itemApi.create({ ...input, code: values.code }), '등록했습니다.')
    } else {
      void form.run(() => itemApi.update(item.id, input), '저장했습니다.')
    }
  }

  return (
    <FormPanel
      record={item}
      title={item ? '품목 수정' : '품목 등록'}
      notice={notice}
      error={form.generalError}
      onClose={onClose}
    >
      {/* noValidate: 브라우저 검증 대신 서버 검증 결과를 그대로 보여준다. 검증의 기준은 항상 서버다 */}
      <form onSubmit={handleSubmit} noValidate>
        <Field
          label="품목 코드"
          error={form.fieldError('code')}
          hint={item ? '코드는 바꿀 수 없습니다.' : '영문 대문자·숫자·하이픈(-) 2~20자. 등록 후 바꿀 수 없습니다.'}
        >
          <input
            value={values.code}
            onChange={(e) => set('code', e.target.value.toUpperCase())}
            disabled={item !== null}
            maxLength={20}
            placeholder="예: BOLT-M10"
            autoFocus={item === null}
          />
        </Field>
        <Field label="품목명" error={form.fieldError('name')}>
          <input value={values.name} onChange={(e) => set('name', e.target.value)} maxLength={100} />
        </Field>
        <Field label="규격" error={form.fieldError('spec')}>
          <input
            value={values.spec}
            onChange={(e) => set('spec', e.target.value)}
            maxLength={100}
            placeholder="예: M10x30"
          />
        </Field>
        <Field label="단위" error={form.fieldError('unit')}>
          <select value={values.unit} onChange={(e) => set('unit', e.target.value as Unit)}>
            {UNITS.map((unit) => (
              <option key={unit.value} value={unit.value}>
                {unit.label}
              </option>
            ))}
          </select>
        </Field>

        <div className="form-actions">
          <button type="submit" className="btn" disabled={form.busy}>
            {item ? '저장' : '등록'}
          </button>
          {item && (
            <ActivationButton
              record={item}
              busy={form.busy}
              onDeactivate={() => void form.run(() => itemApi.deactivate(item.id), '사용 중지했습니다.')}
              onActivate={() => void form.run(() => itemApi.activate(item.id), '다시 사용합니다.')}
            />
          )}
        </div>
      </form>
    </FormPanel>
  )
}
