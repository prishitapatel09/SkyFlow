import dayjs from 'dayjs'
import utc from 'dayjs/plugin/utc'

dayjs.extend(utc)

/** Flight times come back as UTC instants and are displayed as such, like a real timetable. */
export function formatDateTime(iso: string): string {
  return dayjs.utc(iso).format('ddd D MMM YYYY, HH:mm') + ' UTC'
}

export function formatTime(iso: string): string {
  return dayjs.utc(iso).format('HH:mm')
}

export function formatDate(iso: string): string {
  return dayjs.utc(iso).format('ddd D MMM YYYY')
}

export function formatDuration(minutes: number): string {
  const hours = Math.floor(minutes / 60)
  const remainder = minutes % 60
  if (hours === 0) {
    return `${remainder}m`
  }
  return remainder === 0 ? `${hours}h` : `${hours}h ${remainder}m`
}

export function formatMoney(amount: number, currency = 'usd'): string {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: currency.toUpperCase(),
  }).format(amount)
}

export function todayIso(): string {
  return dayjs.utc().format('YYYY-MM-DD')
}

/** Countdown label for a seat hold, e.g. "12m 04s left". */
export function timeLeft(iso: string): string {
  const seconds = dayjs.utc(iso).diff(dayjs.utc(), 'second')
  if (seconds <= 0) {
    return 'expired'
  }
  const minutes = Math.floor(seconds / 60)
  return `${minutes}m ${String(seconds % 60).padStart(2, '0')}s left`
}
