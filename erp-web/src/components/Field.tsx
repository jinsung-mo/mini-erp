import type { ReactNode } from 'react'

type Props = {
  label: string
  error?: string
  hint?: string
  children: ReactNode
}

/** 입력 필드 하나: 라벨 + 입력 + (서버가 알려준) 오류 또는 안내 문구 */
export function Field({ label, error, hint, children }: Props) {
  return (
    <label className={error ? 'field field-invalid' : 'field'}>
      <span className="field-label">{label}</span>
      {children}
      {error ? <span className="field-error">{error}</span> : hint && <span className="field-hint">{hint}</span>}
    </label>
  )
}

type FieldSetProps = {
  legend: string
  error?: string
  hint?: string
  children: ReactNode
}

/** 선택지 여러 개(체크박스 묶음)를 한 항목으로 묶는다. label 하나에 입력을 여러 개 넣지 않으려고 fieldset을 쓴다 */
export function FieldSet({ legend, error, hint, children }: FieldSetProps) {
  return (
    <fieldset className={error ? 'field field-invalid' : 'field'}>
      <legend className="field-label">{legend}</legend>
      <div className="choices">{children}</div>
      {error ? <span className="field-error">{error}</span> : hint && <span className="field-hint">{hint}</span>}
    </fieldset>
  )
}
