import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { api, errorMessage } from '@/lib/api'
import Spinner from '@/components/Spinner'
import { formatDateTime, formatMoney, timeLeft } from '@/lib/format'
import { useAuth } from '@/stores/auth'
import type { BookingCreated } from '@/lib/types'

interface PassengerDraft {
  fullName: string
  passportNumber: string
}

export default function Checkout() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const user = useAuth((state) => state.user)

  const flightId = Number(params.get('flightId'))
  const seatCount = Math.min(Math.max(Number(params.get('passengers') ?? 1), 1), 9)

  const [passengers, setPassengers] = useState<PassengerDraft[]>(() =>
    Array.from({ length: seatCount }, () => ({ fullName: '', passportNumber: '' })),
  )
  const [contactEmail, setContactEmail] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [created, setCreated] = useState<BookingCreated | null>(null)
  const [holdLabel, setHoldLabel] = useState('')

  const { data: flight, isLoading, isError } = useQuery({
    queryKey: ['flight', flightId],
    queryFn: () => api.flights.get(flightId),
    enabled: Number.isFinite(flightId) && flightId > 0,
  })

  useEffect(() => {
    if (user?.email) {
      setContactEmail(user.email)
    }
    if (user?.name) {
      setPassengers((current) =>
        current.map((passenger, index) =>
          index === 0 && !passenger.fullName ? { ...passenger, fullName: user.name } : passenger,
        ),
      )
    }
  }, [user])

  // Seats are held for a fixed window server-side; showing the countdown is the only honest way
  // to explain why a checkout can expire.
  useEffect(() => {
    if (!created?.booking.holdExpiresAt) {
      return
    }
    const expiry = created.booking.holdExpiresAt
    const update = () => setHoldLabel(timeLeft(expiry))
    update()
    const timer = window.setInterval(update, 1000)
    return () => window.clearInterval(timer)
  }, [created])

  const total = useMemo(
    () => (flight ? flight.price * passengers.length : 0),
    [flight, passengers.length],
  )

  function updatePassenger(index: number, field: keyof PassengerDraft, value: string) {
    setPassengers((current) =>
      current.map((passenger, position) =>
        position === index ? { ...passenger, [field]: value } : passenger,
      ),
    )
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    if (!flight || submitting) {
      return
    }
    if (passengers.some((passenger) => !passenger.fullName.trim())) {
      toast.error('Every traveller needs a name.')
      return
    }

    setSubmitting(true)
    try {
      const result = await api.bookings.create(
        flight.id,
        passengers.map((passenger) => ({
          fullName: passenger.fullName.trim(),
          passportNumber: passenger.passportNumber.trim() || undefined,
        })),
        contactEmail.trim() || undefined,
        passengers[0].fullName.trim(),
      )
      setCreated(result)
      toast.success(`Seats held — reference ${result.booking.bookingReference}`)
    } catch (error) {
      // A 409 here means the flight sold out between search and checkout, which is worth
      // saying plainly rather than as a generic failure.
      toast.error(errorMessage(error, 'Could not hold those seats'))
    } finally {
      setSubmitting(false)
    }
  }

  if (!Number.isFinite(flightId) || flightId <= 0) {
    return (
      <section className="section">
        <div className="container card empty-state">
          <h3>No flight selected</h3>
          <Link to="/search" className="btn">
            Find a flight
          </Link>
        </div>
      </section>
    )
  }

  if (isLoading) {
    return <Spinner label="Loading flight…" />
  }

  if (isError || !flight) {
    return (
      <section className="section">
        <div className="container card empty-state">
          <h3>That flight is no longer available</h3>
          <Link to="/search" className="btn">
            Back to search
          </Link>
        </div>
      </section>
    )
  }

  return (
    <section className="section">
      <div className="container stack">
        <h1 className="section-title" style={{ marginBottom: 0 }}>
          Checkout
        </h1>

        <div className="grid grid-2" style={{ alignItems: 'start' }}>
          <div className="stack">
            {created ? (
              <div className="card stack">
                <div>
                  <span className="badge badge-warning">Awaiting payment</span>
                  <h2 style={{ fontSize: '1.2rem', marginTop: 12 }}>
                    Reference {created.booking.bookingReference}
                  </h2>
                  <p className="muted small" style={{ margin: 0 }}>
                    {holdLabel === 'expired'
                      ? 'This hold has expired. The seats have gone back on sale.'
                      : `Your seats are held — ${holdLabel}.`}
                  </p>
                </div>

                <p className="small">
                  Payment is handled by Stripe. This build stops at the payment intent: the
                  client secret below is what a Stripe Elements form would confirm, and
                  payment-service reconciles the result over its webhook.
                </p>

                <div className="card" style={{ background: 'var(--canvas)' }}>
                  <div className="small muted">Client secret</div>
                  <div className="mono" style={{ wordBreak: 'break-all' }}>
                    {created.payment.clientSecret}
                  </div>
                </div>

                <div className="row">
                  <button
                    type="button"
                    className="btn"
                    onClick={() => navigate(`/payment/${created.payment.paymentIntentId}`)}
                  >
                    Check payment status
                  </button>
                  <Link to="/bookings" className="btn btn-secondary">
                    My bookings
                  </Link>
                </div>
              </div>
            ) : (
              <form className="card stack" onSubmit={submit}>
                <h2 style={{ fontSize: '1.1rem', margin: 0 }}>Travellers</h2>

                {passengers.map((passenger, index) => (
                  <div key={index} className="grid grid-2" style={{ gap: 12 }}>
                    <div>
                      <label htmlFor={`name-${index}`}>
                        Traveller {index + 1} full name
                      </label>
                      <input
                        id={`name-${index}`}
                        required
                        value={passenger.fullName}
                        onChange={(event) =>
                          updatePassenger(index, 'fullName', event.target.value)
                        }
                      />
                    </div>
                    <div>
                      <label htmlFor={`passport-${index}`}>Passport (optional)</label>
                      <input
                        id={`passport-${index}`}
                        value={passenger.passportNumber}
                        onChange={(event) =>
                          updatePassenger(index, 'passportNumber', event.target.value)
                        }
                      />
                    </div>
                  </div>
                ))}

                <div>
                  <label htmlFor="contactEmail">Confirmation email</label>
                  <input
                    id="contactEmail"
                    type="email"
                    required
                    value={contactEmail}
                    onChange={(event) => setContactEmail(event.target.value)}
                  />
                </div>

                <button type="submit" className="btn btn-block" disabled={submitting}>
                  {submitting ? <span className="spinner" /> : `Hold seats — ${formatMoney(total)}`}
                </button>
                <p className="small muted" style={{ margin: 0 }}>
                  Holding seats does not charge you. You have 15 minutes to pay.
                </p>
              </form>
            )}
          </div>

          <aside className="card stack">
            <h2 style={{ fontSize: '1.1rem', margin: 0 }}>Your flight</h2>
            <div>
              <div style={{ fontSize: '1.3rem', fontWeight: 700 }}>
                {flight.departureAirport.code} → {flight.arrivalAirport.code}
              </div>
              <div className="muted small mono">{flight.flightNumber}</div>
            </div>
            <table className="table">
              <tbody>
                <tr>
                  <th>Departs</th>
                  <td>{formatDateTime(flight.departureTime)}</td>
                </tr>
                <tr>
                  <th>Arrives</th>
                  <td>{formatDateTime(flight.arrivalTime)}</td>
                </tr>
                <tr>
                  <th>Travellers</th>
                  <td>{passengers.length}</td>
                </tr>
                <tr>
                  <th>Per seat</th>
                  <td>{formatMoney(flight.price)}</td>
                </tr>
                <tr>
                  <th>Total</th>
                  <td>
                    <strong>{formatMoney(total)}</strong>
                  </td>
                </tr>
              </tbody>
            </table>
          </aside>
        </div>
      </div>
    </section>
  )
}
