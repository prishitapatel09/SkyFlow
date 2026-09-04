import { Link } from 'react-router-dom'
import { formatDate, formatDuration, formatMoney, formatTime } from '@/lib/format'
import type { Flight } from '@/lib/types'

interface Props {
  flight: Flight
  passengers?: number
  action?: 'book' | 'view' | 'none'
}

/** Seat pressure is worth showing: it is why a price can disappear between search and checkout. */
function seatsBadge(flight: Flight) {
  if (flight.availableSeats === 0) {
    return <span className="badge badge-danger">Sold out</span>
  }
  if (flight.availableSeats <= 10) {
    return <span className="badge badge-warning">{flight.availableSeats} seats left</span>
  }
  return <span className="badge badge-neutral">{flight.availableSeats} seats</span>
}

export default function FlightCard({ flight, passengers = 1, action = 'book' }: Props) {
  const total = flight.price * passengers

  return (
    <div className="flight-row">
      <div>
        <div className="flight-leg">
          <div className="flight-endpoint">
            <div className="flight-endpoint-time">{formatTime(flight.departureTime)}</div>
            <div className="flight-endpoint-code">{flight.departureAirport.code}</div>
          </div>

          <div className="flight-path">
            {formatDuration(flight.durationMinutes)}
            <div className="flight-path-line" />
            direct
          </div>

          <div className="flight-endpoint">
            <div className="flight-endpoint-time">{formatTime(flight.arrivalTime)}</div>
            <div className="flight-endpoint-code">{flight.arrivalAirport.code}</div>
          </div>
        </div>

        <div className="flight-meta row" style={{ marginTop: 8 }}>
          <strong className="mono">{flight.flightNumber}</strong>
          <span>{formatDate(flight.departureTime)}</span>
          {flight.boardingGate && <span>Gate {flight.boardingGate}</span>}
          {seatsBadge(flight)}
        </div>
      </div>

      <div className="flight-price">
        <div className="flight-price-amount">{formatMoney(flight.price)}</div>
        <div className="small muted">
          {passengers > 1 ? `${formatMoney(total)} for ${passengers}` : 'per traveller'}
        </div>

        {action !== 'none' && (
          <div style={{ marginTop: 10 }}>
            {action === 'book' && flight.availableSeats >= passengers ? (
              <Link
                className="btn btn-sm"
                to={`/checkout?flightId=${flight.id}&passengers=${passengers}`}
              >
                Select
              </Link>
            ) : (
              <Link className="btn btn-secondary btn-sm" to={`/flights/${flight.id}`}>
                Details
              </Link>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
