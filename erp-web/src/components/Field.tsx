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
