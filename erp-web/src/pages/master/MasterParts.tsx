import type { ReactNode } from 'react'
import type { ApiError } from '../../api/client'
import type { MasterRecord } from '../../api/master'
import { formatDateTime } from '../../format'
import type { ActiveFilter } from './useMasterPage'

/** 기준정보 화면(품목·거래처·창고)이 함께 쓰는 화면 부품 */

export function ErrorAlert({ error }: { error: ApiError }) {
  return (
    <div className="alert" role="alert">
      <div className="alert-title">
        <code>{error.code}</code> {error.message}
      </div>
    </div>
  )
}

export function StatusTag({ active }: { active: boolean }) {
  return active ? <span className="tag tag-on">사용</span> : <span className="tag tag-off">중지</span>
}

/** 조회 조건: 사용 여부 */
export function ActiveFilterSelect({ value, onChange }: { value: ActiveFilter; onChange: (value: ActiveFilter) => void }) {
  return (
    <label>
      사용 여부
      <select name="active" value={value} onChange={(e) => onChange(e.target.value as ActiveFilter)}>
        <option value="all">전체</option>
        <option value="active">사용</option>
        <option value="inactive">사용 중지</option>
      </select>
    </label>
  )
}

type GridProps<T extends MasterRecord> = {
  /** 화면마다 다른 컬럼. 상태·수정자·수정일시는 모든 기준정보 목록 끝에 공통으로 붙는다 */
  columns: string[]
  rows: T[]
  loading: boolean
  error: ApiError | null
  selectedId: number | null
  emptyText: string
  onSelect: (row: T) => void
  renderCells: (row: T) => ReactNode
}

/** 조회 결과 목록. 행을 클릭하거나 Enter를 누르면 오른쪽 폼에서 수정한다 */
export function MasterGrid<T extends MasterRecord>({
  columns,
  rows,
  loading,
  error,
  selectedId,
  emptyText,
  onSelect,
  renderCells,
}: GridProps<T>) {
  const headers = [...columns, '상태', '수정자', '수정일시']
  return (
    <div className="grid-panel">
      <div className="grid-meta">{loading ? '조회 중…' : `${rows.length}건`}</div>
      {error ? (
        <ErrorAlert error={error} />
      ) : (
        <table className="grid">
          <thead>
            <tr>
              {headers.map((header) => (
                <th key={header}>{header}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr
                key={row.id}
                className={[row.id === selectedId && 'row-selected', !row.active && 'row-inactive']
                  .filter(Boolean)
                  .join(' ')}
                onClick={() => onSelect(row)}
                onKeyDown={(e) => e.key === 'Enter' && onSelect(row)}
                tabIndex={0}
              >
                {renderCells(row)}
                <td>
                  <StatusTag active={row.active} />
                </td>
                <td>{row.updatedBy}</td>
                <td>{formatDateTime(row.updatedAt)}</td>
              </tr>
            ))}
            {!loading && rows.length === 0 && (
              <tr>
                <td colSpan={headers.length} className="grid-empty">
                  {emptyText}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      )}
    </div>
  )
}

type FormPanelProps = {
  /** null이면 신규 등록 */
  record: MasterRecord | null
  title: string
  notice: string | null
  error: ApiError | null
  onClose: () => void
  children: ReactNode
}

/** 오른쪽 입력 패널의 틀: 제목과 코드, 저장 결과 안내, 오류, (입력 항목), 등록·수정 이력 */
export function FormPanel({ record, title, notice, error, onClose, children }: FormPanelProps) {
  return (
    <aside className="form-panel" aria-label={title}>
      <div className="form-header">
        <h2>{title}</h2>
        {record && <code className="form-code">{record.code}</code>}
        <button type="button" className="btn-icon" onClick={onClose} aria-label="닫기">
          ×
        </button>
      </div>

      {notice && <div className="notice">{notice}</div>}
      {error && <ErrorAlert error={error} />}

      {children}

      {record && (
        <dl className="audit">
          <dt>등록</dt>
          <dd>
            {record.createdBy} · {formatDateTime(record.createdAt)}
          </dd>
          <dt>수정</dt>
          <dd>
            {record.updatedBy} · {formatDateTime(record.updatedAt)}
          </dd>
        </dl>
      )}
    </aside>
  )
}

type ActivationProps = {
  record: MasterRecord
  busy: boolean
  onDeactivate: () => void
  onActivate: () => void
}

/** 사용 중지 / 사용 재개. 중지하기 전에 "과거 거래는 그대로 남는다"는 것을 확인받는다 */
export function ActivationButton({ record, busy, onDeactivate, onActivate }: ActivationProps) {
  if (!record.active) {
    return (
      <button type="button" className="btn btn-secondary" disabled={busy} onClick={onActivate}>
        사용 재개
      </button>
    )
  }
  return (
    <button
      type="button"
      className="btn btn-danger-outline"
      disabled={busy}
      onClick={() => {
        const message = `${record.code}을(를) 사용 중지할까요?\n새 거래에서는 선택할 수 없지만, 과거 거래 기록은 그대로 남습니다.`
        if (window.confirm(message)) onDeactivate()
      }}
    >
      사용 중지
    </button>
  )
}
