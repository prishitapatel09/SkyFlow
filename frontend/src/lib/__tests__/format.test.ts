import { describe, expect, it } from 'vitest'
import { formatDuration, formatMoney, formatTime, timeLeft } from '../format'
import dayjs from 'dayjs'

describe('formatDuration', () => {
  it('renders whole hours without a stray 0m', () => {
    expect(formatDuration(120)).toBe('2h')
  })

  it('renders hours and minutes', () => {
    expect(formatDuration(395)).toBe('6h 35m')
  })

  it('renders sub-hour durations as minutes', () => {
    expect(formatDuration(45)).toBe('45m')
  })
})

describe('formatTime', () => {
  it('renders UTC regardless of the browser timezone', () => {
    // A flight timetable that shifts with the reader's timezone is worse than useless.
    expect(formatTime('2026-09-11T13:45:00Z')).toBe('13:45')
  })
})

describe('formatMoney', () => {
  it('formats USD with a symbol and two decimals', () => {
    expect(formatMoney(349)).toBe('$349.00')
  })

  it('honours the currency the booking was priced in', () => {
    expect(formatMoney(349, 'eur')).toContain('349.00')
  })
})

describe('timeLeft', () => {
  it('counts down a live hold', () => {
    const expiry = dayjs.utc().add(90, 'second').toISOString()
    expect(timeLeft(expiry)).toMatch(/^1m \d{2}s left$/)
  })

  it('reports an elapsed hold as expired', () => {
    expect(timeLeft(dayjs.utc().subtract(1, 'minute').toISOString())).toBe('expired')
  })
})
