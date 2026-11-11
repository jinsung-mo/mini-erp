import type { StockTransaction } from '../../api/inventory'
import { formatDateTime, formatQuantity } from '../../format'

/** +60 / -2. 들어온 것과 나간 것을 색으로도 나눈다 */
export function SignedQuantity({ value }: { value: number }) {
  return (
    <span className={value > 0 ? 'qty-in' : 'qty-out'}>
      {value > 0 ? '+' : ''}
      {formatQuantity(value)}
    </span>
  )
}

/** 원장 표. showItem이 false면 품목·창고 열을 뺀다 (한 품목 × 창고의 수불만 볼 때) */
export function TransactionTable({ rows, showItem = true }: { rows: StockTransaction[]; showItem?: boolean }) {
  return (
    <table className="grid">
      <thead>
        <tr>
          <th>일자</th>
          <th>종류</th>
          <th>문서 번호</th>
          {showItem && <th>품목</th>}
          {showItem && <th>창고</th>}
          <th className="num">수량</th>
          <th className="num">직후 재고</th>
          <th>사유</th>
          <th>처리자</th>
          <th>처리일시</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((t) => (
          <tr key={t.id} className="row-static">
            <td>{t.txnDate}</td>
            <td>{t.typeLabel}</td>
            <td>
              <code>{t.sourceNo}</code>
            </td>
            {showItem && (
              <td>
                <code>{t.item.code}</code> {t.item.name}
              </td>
            )}
            {showItem && <td>{t.warehouse.name}</td>}
            <td className="num">
              <SignedQuantity value={t.quantity} />
            </td>
            <td className="num">{formatQuantity(t.balanceAfter)}</td>
            <td>{t.reason ?? '-'}</td>
            <td>{t.actor}</td>
            <td>{formatDateTime(t.occurredAt)}</td>
          </tr>
        ))}
        {rows.length === 0 && (
          <tr>
            <td colSpan={showItem ? 10 : 8} className="grid-empty">
              수불 기록이 없습니다.
            </td>
          </tr>
        )}
      </tbody>
    </table>
  )
}
