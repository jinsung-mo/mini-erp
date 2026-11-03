import { type FormEvent, useState } from 'react'
import { type ApiError, toApiError } from '../../api/client'
import { type Item, type ItemInput, type Unit, itemApi, UNITS } from '../../api/items'
import { Field } from '../../components/Field'
import { formatDateTime } from '../../format'

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
  const [error, setError] = useState<ApiError | null>(null)
  const [busy, setBusy] = useState(false)

  function set<K extends keyof Values>(key: K, value: Values[K]) {
    setValues((prev) => ({ ...prev, [key]: value }))
  }

  /** 서버가 돌려준 필드 오류(ProblemDetail.errors)를 해당 입력 아래에 보여준다 */
  function fieldError(field: keyof Values): string | undefined {
    if (!error) return undefined
    if (field === 'code' && error.code === 'DUPLICATE_CODE') return error.message
    return error.fieldErrors.find((fieldError) => fieldError.field === field)?.message ?? undefined
  }

  const generalError = error && error.fieldErrors.length === 0 && error.code !== 'DUPLICATE_CODE' ? error : null

  async function run(action: () => Promise<Item>, message: string) {
    setBusy(true)
    setError(null)
    try {
      onSaved(await action(), message)
    } catch (e) {
      setError(toApiError(e))
    } finally {
      setBusy(false)
    }
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const input: ItemInput = { name: values.name, spec: values.spec, unit: values.unit }
    if (item === null) {
      void run(() => itemApi.create({ ...input, code: values.code }), '등록했습니다.')
    } else {
      void run(() => itemApi.update(item.id, input), '저장했습니다.')
    }
  }

  function handleDeactivate(target: Item) {
    if (!window.confirm(`${target.code}을(를) 사용 중지할까요?\n새 거래에서는 선택할 수 없지만, 과거 거래 기록은 그대로 남습니다.`)) {
      return
    }
    void run(() => itemApi.deactivate(target.id), '사용 중지했습니다.')
  }

  return (
    <aside className="form-panel" aria-label={item ? '품목 수정' : '품목 등록'}>
      <div className="form-header">
        <h2>{item ? '품목 수정' : '품목 등록'}</h2>
        {item && <code className="form-code">{item.code}</code>}
        <button type="button" className="btn-icon" onClick={onClose} aria-label="닫기">
          ×
        </button>
      </div>

      {notice && <div className="notice">{notice}</div>}
      {generalError && (
        <div className="alert" role="alert">
          <div className="alert-title">
            <code>{generalError.code}</code> {generalError.message}
          </div>
        </div>
      )}

      {/* noValidate: 브라우저 검증 대신 서버 검증 결과를 그대로 보여준다. 검증의 기준은 항상 서버다 */}
      <form onSubmit={handleSubmit} noValidate>
        <Field
          label="품목 코드"
          error={fieldError('code')}
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
        <Field label="품목명" error={fieldError('name')}>
          <input value={values.name} onChange={(e) => set('name', e.target.value)} maxLength={100} />
        </Field>
        <Field label="규격" error={fieldError('spec')}>
          <input
            value={values.spec}
            onChange={(e) => set('spec', e.target.value)}
            maxLength={100}
            placeholder="예: M10x30"
          />
        </Field>
        <Field label="단위" error={fieldError('unit')}>
          <select value={values.unit} onChange={(e) => set('unit', e.target.value as Unit)}>
            {UNITS.map((unit) => (
              <option key={unit.value} value={unit.value}>
                {unit.label}
              </option>
            ))}
          </select>
        </Field>

        <div className="form-actions">
          <button type="submit" className="btn" disabled={busy}>
            {item ? '저장' : '등록'}
          </button>
          {item &&
            (item.active ? (
              <button type="button" className="btn btn-danger-outline" disabled={busy} onClick={() => handleDeactivate(item)}>
                사용 중지
              </button>
            ) : (
              <button
                type="button"
                className="btn btn-secondary"
                disabled={busy}
                onClick={() => void run(() => itemApi.activate(item.id), '다시 사용합니다.')}
              >
                사용 재개
              </button>
            ))}
        </div>
      </form>

      {item && (
        <dl className="audit">
          <dt>등록</dt>
          <dd>
            {item.createdBy} · {formatDateTime(item.createdAt)}
          </dd>
          <dt>수정</dt>
          <dd>
            {item.updatedBy} · {formatDateTime(item.updatedAt)}
          </dd>
        </dl>
      )}
    </aside>
  )
}
