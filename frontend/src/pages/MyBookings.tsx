import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { api, errorMessage } from '@/lib/api'
import Spinner from '@/components/Spinner'
import { formatDateTime, formatMoney } from '@/lib/format'
import type { Booking, BookingStatus } from '@/lib/types'

const STATUS_STYLES: Record<BookingStatus, string> = {
  CONFIRMED: 'badge badge-success',
  PENDING_PAYMENT: 'badge badge-warning',
  EXPIRED: 'badge badge-neutral',
  CANCELLED: 'badge badge-neutral',
  FAILED: 'badge badge-danger',
}

const STATUS_LABELS: Record<BookingStatus, string> = {
  CONFIRMED: 'Confirmed',
  PENDING_PAYMENT: 'Awaiting payment',
  EXPIRED: 'Hold expired',
  CANCELLED: 'Cancelled',
  FAILED: 'Payment failed',
}

function canCancel(booking: Booking) {
  return booking.status === 'CONFIRMED' || booking.status === 'PENDING_PAYMENT'
}

export default function MyBookings() {
  const queryClient = useQueryClient()

  const { data: bookings, isLoading, isError, refetch } = useQuery({
    queryKey: ['my-bookings'],
    queryFn: api.bookings.mine,
  })

  const cancel = useMutation({
    mutationFn: ({ id, reason }: { id: number; reason: string }) =>
      api.bookings.cancel(id, reason),
    onSuccess: () => {
      toast.success('Booking cancelled')
      void queryClient.invalidateQueries({ queryKey: ['my-bookings'] })
    },
    onError: (error) => toast.error(errorMessage(error, 'Could not cancel that booking')),
  })

  function requestCancel(booking: Booking) {
    // Cancelling releases seats and triggers a refund, so it is confirmed explicitly - and only
    // ever by the traveller, never by the assistant.
    const confirmed = window.confirm(
      `Cancel booking ${booking.bookingReference}? ` +
        (booking.status === 'CONFIRMED'
          ? 'Your payment will be refunded to the original card.'
          : 'The held seats will be released.'),
    )
    if (confirmed) {
      cancel.mutate({ id: booking.id, reason: 'requested_by_customer' })
    }
  }

  if (isLoading) {
    return <Spinner label="Loading your bookings…" />
  }

  if (isError) {
    return (
      <section className="section">
        <div className="container card empty-state">
          <p>We could not load your bookings.</p>
          <button type="button" className="btn btn-secondary" onClick={() => void refetch()}>
            Try again
          </button>
        </div>
      </section>
    )
  }

  return (
    <section className="section">
      <div className="container stack">
        <h1 className="section-title" style={{ marginBottom: 0 }}>
          My bookings
        </h1>

        {(!bookings || bookings.length === 0) && (
          <div className="card empty-state">
            <h3>Nothing booked yet</h3>
            <p>Your bookings will show up here once you have one.</p>
          </div>
        )}

        {bookings?.map((booking) => (
          <article key={booking.id} className="card">
            <div className="spread">
              <div>
                <div className="row" style={{ gap: 10 }}>
                  <strong style={{ fontSize: '1.1rem' }}>
                    {booking.originCode} → {booking.destinationCode}
                  </strong>
                  <span className={STATUS_STYLES[booking.status]}>
                    {STATUS_LABELS[booking.status]}
                  </span>
                </div>
                <div className="muted small">
                  <span className="mono">{booking.bookingReference}</span> · flight{' '}
                  <span className="mono">{booking.flightNumber}</span> ·{' '}
                  {formatDateTime(booking.departureTime)}
                </div>
              </div>

              <div className="row">
                <div style={{ textAlign: 'right' }}>
                  <div style={{ fontWeight: 700 }}>
                    {formatMoney(booking.totalAmount, booking.currency)}
                  </div>
                  <div className="small muted">
                    {booking.seats} {booking.seats === 1 ? 'seat' : 'seats'}
                  </div>
                </div>
                {canCancel(booking) && (
                  <button
                    type="button"
                    className="btn btn-secondary btn-sm"
                    onClick={() => requestCancel(booking)}
                    disabled={cancel.isPending}
                  >
                    Cancel
                  </button>
                )}
              </div>
            </div>

            {booking.passengers.length > 0 && (
              <div className="small muted" style={{ marginTop: 12 }}>
                {booking.passengers.map((passenger) => passenger.fullName).join(' · ')}
              </div>
            )}
          </article>
        ))}
      </div>
    </section>
  )
}
