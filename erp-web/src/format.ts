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

/** 1234567891 → 123-45-67891. 서버는 숫자 10자리로만 저장하고, 하이픈은 화면이 붙인다 */
export function formatBusinessNo(digits: string): string {
  return /^\d{10}$/.test(digits) ? `${digits.slice(0, 3)}-${digits.slice(3, 5)}-${digits.slice(5)}` : digits
}
