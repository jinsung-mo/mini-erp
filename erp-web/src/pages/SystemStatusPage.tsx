import { useEffect, useState } from 'react'
import { api, ApiError } from '../api/client'

type SystemStatus = {
  application: string
  databaseName: string
  databaseVersion: string
  databaseTime: string
}

type CheckState =
  | { kind: 'checking' }
  | { kind: 'ok'; status: SystemStatus; checkedAt: Date }
  | { kind: 'error'; error: ApiError; checkedAt: Date }

type TierHealth = 'up' | 'down' | 'checking' | 'unknown'

type Tier = { name: string; tech: string; address: string; health: TierHealth }

const HEALTH_LABEL: Record<TierHealth, string> = {
  up: '정상',
  down: '응답 없음',
  checking: '확인 중',
  unknown: '알 수 없음',
}

/**
 * 화면 하나로 3계층(WEB → WAS → DB)이 모두 이어졌는지 보여준다.
 * /api/system/status는 DB만 조회하므로, API가 에러(ProblemDetail)로 응답했다면 API는 살아 있고 DB 쪽이 문제다.
 */
function toTiers(state: CheckState): Tier[] {
  let was: TierHealth = 'checking'
  let db: TierHealth = 'checking'
  if (state.kind === 'ok') {
    was = 'up'
    db = 'up'
  } else if (state.kind === 'error') {
    was = state.error.fromServer ? 'up' : 'down'
    db = state.error.fromServer ? 'down' : 'unknown'
  }
  return [
    { name: 'WEB', tech: 'React + Vite', address: 'localhost:5173', health: 'up' },
    { name: 'WAS', tech: 'Spring Boot', address: '127.0.0.1:8082', health: was },
    { name: 'DB', tech: 'PostgreSQL', address: '127.0.0.1:55433', health: db },
  ]
}

async function fetchStatus(): Promise<CheckState> {
  try {
    const status = await api<SystemStatus>('/system/status')
    return { kind: 'ok', status, checkedAt: new Date() }
  } catch (e) {
    const error =
      e instanceof ApiError ? e : new ApiError(0, 'UNKNOWN', '알 수 없는 오류가 발생했습니다.', [], false)
    return { kind: 'error', error, checkedAt: new Date() }
  }
}

export function SystemStatusPage() {
  const [state, setState] = useState<CheckState>({ kind: 'checking' })

  useEffect(() => {
    // 응답이 오기 전에 화면을 떠나면(또는 StrictMode가 effect를 두 번 실행하면) 늦게 온 응답은 버린다
    let ignore = false
    void fetchStatus().then((next) => {
      if (!ignore) setState(next)
    })
    return () => {
      ignore = true
    }
  }, [])

  async function check() {
    setState({ kind: 'checking' })
    setState(await fetchStatus())
  }

  return (
    <section className="page">
      <div className="page-header">
        <div>
          <h1>시스템 상태</h1>
          <p className="page-desc">화면 → API → DB 세 계층이 실제로 연결되어 있는지 확인합니다.</p>
        </div>
        <button type="button" className="btn" onClick={() => void check()} disabled={state.kind === 'checking'}>
          {state.kind === 'checking' ? '확인 중…' : '다시 확인'}
        </button>
      </div>

      <ol className="tiers">
        {toTiers(state).map((tier) => (
          <li key={tier.name} className={`tier tier-${tier.health}`}>
            <div className="tier-name">{tier.name}</div>
            <div className="tier-tech">{tier.tech}</div>
            <div className="tier-address">{tier.address}</div>
            <div className="tier-health">
              <span className="dot" aria-hidden="true" />
              {HEALTH_LABEL[tier.health]}
            </div>
          </li>
        ))}
      </ol>

      {state.kind === 'ok' && (
        <dl className="detail">
          <dt>애플리케이션</dt>
          <dd>{state.status.application}</dd>
          <dt>데이터베이스</dt>
          <dd>{state.status.databaseName}</dd>
          <dt>DB 버전</dt>
          <dd>{state.status.databaseVersion}</dd>
          <dt>DB 시각</dt>
          <dd>{new Date(state.status.databaseTime).toLocaleString('ko-KR')}</dd>
          <dt>확인 시각</dt>
          <dd>{state.checkedAt.toLocaleTimeString('ko-KR')}</dd>
        </dl>
      )}

      {state.kind === 'error' && (
        <div className="alert" role="alert">
          <div className="alert-title">
            <code>{state.error.code}</code> {state.error.message}
          </div>
          {state.error.fromServer ? (
            <p className="alert-hint">
              API는 응답했지만 DB 조회에 실패했습니다. <code>docker compose ps</code>로 DB 컨테이너를 확인하고, 원인은
              API 서버 로그에서 찾으세요.
            </p>
          ) : (
            <p className="alert-hint">
              API 서버가 응답하지 않습니다. <code>erp-api</code>에서 <code>./gradlew bootRun</code>이 실행 중인지
              확인하세요.
            </p>
          )}
        </div>
      )}
    </section>
  )
}
