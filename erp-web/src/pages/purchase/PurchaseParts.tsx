import {
  EVENT_LABELS,
  type PurchaseOrderEvent,
  type PurchaseOrderStatus,
  statusLabel,
} from '../../api/purchaseOrders'
import { formatDateTime } from '../../format'

/** 구매 화면(발주·입고)이 함께 쓰는 부품 */

export function PoStatusTag({ status }: { status: PurchaseOrderStatus }) {
  return <span className={`tag tag-po-${status.toLowerCase()}`}>{statusLabel(status)}</span>
}

/** 상태 이력: 누가 언제 무엇을 했는지. 반려·취소·마감 사유와 입고 번호도 함께 보여 준다 */
export function EventTimeline({ events }: { events: PurchaseOrderEvent[] }) {
  return (
    <ol className="timeline">
      {events.map((event, index) => (
        <li key={index} className={`timeline-item timeline-${event.type.toLowerCase()}`}>
          <div className="timeline-head">
            <strong>{EVENT_LABELS[event.type]}</strong>
            <span className="timeline-meta">
              {event.actor} · {formatDateTime(event.occurredAt)}
            </span>
          </div>
          {event.fromStatus && (
            <div className="timeline-meta">
              {statusLabel(event.fromStatus)} → {statusLabel(event.toStatus)}
            </div>
          )}
          {event.reason && <div className="timeline-reason">{event.reason}</div>}
        </li>
      ))}
    </ol>
  )
}
