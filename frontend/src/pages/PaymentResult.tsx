import { Link, useParams } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { useEffect } from 'react'
import { api, errorMessage } from '@/lib/api'
import Spinner from '@/components/Spinner'

const SUCCESS_STATES = new Set(['succeeded'])
const PENDING_STATES = new Set(['processing', 'requires_action', 'requires_confirmation',
  'requires_payment_method'])

/**
 * Landing page after a Stripe confirmation. It asks payment-service to re-read the intent rather
 * than trusting a query parameter, because the webhook may not have arrived yet and a URL is not
 * evidence that anything was paid.
 */
export default function PaymentResult() {
  const { paymentIntentId } = useParams<{ paymentIntentId: string }>()

  const sync = useMutation({
    mutationFn: () => api.payments.sync(paymentIntentId!),
  })

  useEffect(() => {
    if (paymentIntentId) {
      sync.mutate()
    }
    // Deliberately once per payment intent.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [paymentIntentId])

  if (sync.isPending || sync.isIdle) {
    return <Spinner label="Confirming your payment…" />
  }

  if (sync.isError) {
    return (
      <section className="section">
        <div className="container card empty-state">
          <h1 style={{ fontSize: '1.5rem' }}>We could not confirm that payment</h1>
          <p className="muted">{errorMessage(sync.error, 'Try again in a moment.')}</p>
          <div className="row" style={{ justifyContent: 'center' }}>
            <button type="button" className="btn" onClick={() => sync.mutate()}>
              Check again
            </button>
            <Link to="/bookings" className="btn btn-secondary">
              My bookings
            </Link>
          </div>
        </div>
      </section>
    )
  }

  const status = sync.data?.status ?? 'unknown'
  const succeeded = SUCCESS_STATES.has(status)
  const pending = PENDING_STATES.has(status)

  return (
    <section className="section">
      <div className="container card empty-state">
        <h1 style={{ fontSize: '1.6rem' }}>
          {succeeded ? 'Payment received' : pending ? 'Payment still processing' : 'Payment failed'}
        </h1>
        <p className="muted">
          {succeeded
            ? 'Your booking is confirmed and your confirmation email is on its way.'
            : pending
              ? 'Stripe has not finished with this payment yet. Your seats stay held meanwhile.'
              : 'The payment did not go through, so the seats were released.'}
        </p>
        <p className="small muted mono">
          {paymentIntentId} · {status}
        </p>
        <div className="row" style={{ justifyContent: 'center' }}>
          <Link to="/bookings" className="btn">
            View my bookings
          </Link>
          {!succeeded && (
            <Link to="/search" className="btn btn-secondary">
              Search again
            </Link>
          )}
        </div>
      </div>
    </section>
  )
}
