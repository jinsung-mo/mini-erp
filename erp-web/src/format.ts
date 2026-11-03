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
