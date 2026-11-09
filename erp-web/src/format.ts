const dateTimeFormat = new Intl.DateTimeFormat('ko-KR', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
})

/** 서버는 UTC 시점(ISO-8601, ...Z)으로 보내고, 화면은 브라우저 시간대로 보여준다 */
export function formatDateTime(iso: string): string {
  return dateTimeFormat.format(new Date(iso))
}

const quantityFormat = new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 3 })
const moneyFormat = new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 0 })

/** 1234.5 → 1,234.5 (수량은 소수 3자리까지) */
export function formatQuantity(value: number): string {
  return quantityFormat.format(value)
}

/** 88500 → 88,500원. 금액 계산은 서버(BigDecimal)가 하고 화면은 보여 주기만 한다 */
export function formatMoney(value: number): string {
  return `${moneyFormat.format(value)}원`
}

/**
 * 업무 일자(발주일·입고일)는 시점이 아니라 날짜다. 서버는 "2026-11-09" 그대로 주고받고, 화면도 시간대 변환을 하지 않는다.
 * new Date("2026-11-09")는 UTC 자정으로 해석되어 시간대에 따라 하루 전날이 될 수 있으므로 쓰지 않는다.
 */
export function today(): string {
  const now = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}

/** 1234567891 → 123-45-67891. 서버는 숫자 10자리로만 저장하고, 하이픈은 화면이 붙인다 */
export function formatBusinessNo(digits: string): string {
  return /^\d{10}$/.test(digits) ? `${digits.slice(0, 3)}-${digits.slice(3, 5)}-${digits.slice(5)}` : digits
}
