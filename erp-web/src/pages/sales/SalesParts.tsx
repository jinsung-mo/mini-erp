import { SO_EVENT_LABELS, type SalesOrderEvent, type SalesOrderStatus, soStatusLabel } from '../../api/salesOrders'
import { formatDateTime } from '../../format'

/** 판매 화면(수주·출고)이 함께 쓰는 부품 */

export function SoStatusTag({ status }: { status: SalesOrderStatus }) {
  return <span className={`tag tag-so-${status.toLowerCase()}`}>{soStatusLabel(status)}</span>
}

/** 상태 이력. 출고 이력에는 출고 번호, 취소·마감에는 사유가 함께 나온다 */
export function SoEventTimeline({ events }: { events: SalesOrderEvent[] }) {
  return (
    <ol className="timeline">
      {events.map((event, index) => (
        <li key={index} className={`timeline-item timeline-${event.type.toLowerCase()}`}>
          <div className="timeline-head">
            <strong>{SO_EVENT_LABELS[event.type]}</strong>
            <span className="timeline-meta">
              {event.actor} · {formatDateTime(event.occurredAt)}
            </span>
          </div>
          {event.fromStatus && (
            <div className="timeline-meta">
              {soStatusLabel(event.fromStatus)} → {soStatusLabel(event.toStatus)}
            </div>
          )}
          {event.reason && <div className="timeline-reason">{event.reason}</div>}
        </li>
      ))}
    </ol>
  )
}
