import { Link, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import Spinner from '@/components/Spinner'
import { formatDateTime, formatDuration, formatMoney } from '@/lib/format'

export default function FlightDetail() {
  const { id } = useParams<{ id: string }>()

  const { data: flight, isLoading, isError } = useQuery({
    queryKey: ['flight', id],
    queryFn: () => api.flights.get(id!),
    enabled: Boolean(id),
  })

  // Availability has a much shorter TTL server-side than the flight itself, so it is fetched
  // separately rather than read off the cached flight.
  const { data: seats } = useQuery({
    queryKey: ['flight-seats', id],
    queryFn: () => api.flights.seats(id!),
    enabled: Boolean(id),
    staleTime: 15_000,
  })

  if (isLoading) {
    return <Spinner label="Loading flight…" />
  }

  if (isError || !flight) {
    return (
      <section className="section">
        <div className="container card empty-state">
          <h3>Flight not found</h3>
          <Link to="/search" className="btn btn-secondary">
            Back to search
          </Link>
        </div>
      </section>
    )
  }

  const available = seats?.availableSeats ?? flight.availableSeats

  return (
    <section className="section">
      <div className="container stack">
        <div className="spread">
          <div>
            <h1 className="section-title" style={{ marginBottom: 4 }}>
              {flight.departureAirport.code} → {flight.arrivalAirport.code}
            </h1>
            <p className="muted" style={{ margin: 0 }}>
              Flight <span className="mono">{flight.flightNumber}</span> ·{' '}
              {formatDuration(flight.durationMinutes)} · {flight.status}
            </p>
          </div>
          <div className="row">
            <div style={{ textAlign: 'right' }}>
              <div className="flight-price-amount">{formatMoney(flight.price)}</div>
              <div className="small muted">per traveller</div>
            </div>
            {available > 0 ? (
              <Link className="btn" to={`/checkout?flightId=${flight.id}&passengers=1`}>
                Book this flight
              </Link>
            ) : (
              <button type="button" className="btn" disabled>
                Sold out
              </button>
            )}
          </div>
        </div>

        <div className="grid grid-2">
          <div className="card">
            <h3 style={{ fontSize: '1rem' }}>Departure</h3>
            <p style={{ margin: 0 }}>
              <strong>{flight.departureAirport.name}</strong> ({flight.departureAirport.code})
              <br />
              {flight.departureAirport.city?.name}
              <br />
              {formatDateTime(flight.departureTime)}
              {flight.boardingGate && (
                <>
                  <br />
                  Gate {flight.boardingGate}
                </>
              )}
            </p>
          </div>

          <div className="card">
            <h3 style={{ fontSize: '1rem' }}>Arrival</h3>
            <p style={{ margin: 0 }}>
              <strong>{flight.arrivalAirport.name}</strong> ({flight.arrivalAirport.code})
              <br />
              {flight.arrivalAirport.city?.name}
              <br />
              {formatDateTime(flight.arrivalTime)}
            </p>
          </div>
        </div>

        <div className="card">
          <h3 style={{ fontSize: '1rem' }}>Aircraft and seats</h3>
          <table className="table">
            <tbody>
              <tr>
                <th>Aircraft</th>
                <td>{flight.airplane?.modelNumber ?? 'To be assigned'}</td>
              </tr>
              <tr>
                <th>Total seats</th>
                <td>{flight.totalSeats}</td>
              </tr>
              <tr>
                <th>Available now</th>
                <td>
                  {available}
                  {available <= 10 && available > 0 && (
                    <span className="badge badge-warning" style={{ marginLeft: 8 }}>
                      Selling fast
                    </span>
                  )}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>
  )
}
