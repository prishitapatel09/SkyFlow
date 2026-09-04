import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import Spinner from '@/components/Spinner'
import { formatDateTime, formatMoney } from '@/lib/format'
import type { ClusterStatus } from '@/lib/types'

const ROLE_BADGES: Record<ClusterStatus['role'], string> = {
  LEADER: 'badge badge-brand',
  CANDIDATE: 'badge badge-warning',
  FOLLOWER: 'badge badge-neutral',
}

function relativeSeconds(epochMillis?: number): string {
  if (!epochMillis) {
    return 'never'
  }
  const seconds = Math.max(0, Math.round((Date.now() - epochMillis) / 1000))
  return `${seconds}s ago`
}

/**
 * Operations view. The cluster panel is the interesting half: it shows which booking-service
 * replica currently holds the master role, what term it was elected in, and how loaded each
 * worker is. Kill the leader pod and this is where you watch a new one take over.
 */
export default function AdminDashboard() {
  const { data: cluster, isLoading: clusterLoading } = useQuery({
    queryKey: ['cluster-status'],
    queryFn: api.cluster.status,
    // Elections resolve in a couple of seconds, so a slow poll would miss the interesting part.
    refetchInterval: 2000,
    staleTime: 0,
  })

  const { data: bookings, isLoading: bookingsLoading } = useQuery({
    queryKey: ['all-bookings'],
    queryFn: () => api.bookings.all(),
    refetchInterval: 30_000,
  })

  const confirmed = bookings?.filter((booking) => booking.status === 'CONFIRMED') ?? []
  const revenue = confirmed.reduce((sum, booking) => sum + booking.totalAmount, 0)

  return (
    <section className="section">
      <div className="container stack">
        <h1 className="section-title" style={{ marginBottom: 0 }}>
          Operations
        </h1>

        <div className="grid grid-4">
          <div className="card stat">
            <div className="stat-value">{bookings?.length ?? '—'}</div>
            <div className="stat-label">Bookings</div>
          </div>
          <div className="card stat">
            <div className="stat-value">{confirmed.length}</div>
            <div className="stat-label">Confirmed</div>
          </div>
          <div className="card stat">
            <div className="stat-value">{formatMoney(revenue)}</div>
            <div className="stat-label">Confirmed revenue</div>
          </div>
          <div className="card stat">
            <div className="stat-value">{cluster ? cluster.pendingTasks : '—'}</div>
            <div className="stat-label">Queued tasks</div>
          </div>
        </div>

        <div className="card">
          <div className="spread">
            <div>
              <h2 style={{ fontSize: '1.1rem', marginBottom: 4 }}>Booking service cluster</h2>
              <p className="muted small" style={{ margin: 0 }}>
                Raft-inspired election over gRPC heartbeats. One replica holds the master role and
                hands tasks to the rest.
              </p>
            </div>
            {cluster && <span className={ROLE_BADGES[cluster.role]}>{cluster.role}</span>}
          </div>

          {clusterLoading && <Spinner label="Reading cluster state…" />}

          {cluster && (
            <>
              <table className="table" style={{ marginTop: 16 }}>
                <tbody>
                  <tr>
                    <th>This node</th>
                    <td className="mono">{cluster.nodeId}</td>
                  </tr>
                  <tr>
                    <th>Leader</th>
                    <td className="mono">{cluster.leaderId ?? 'election in progress'}</td>
                  </tr>
                  <tr>
                    <th>Term</th>
                    <td>
                      {cluster.term} <span className="muted">(quorum {cluster.quorum})</span>
                    </td>
                  </tr>
                  <tr>
                    <th>Tasks</th>
                    <td>
                      {cluster.pendingTasks} queued · {cluster.inFlightTasks} in flight ·{' '}
                      {cluster.localInFlightTasks} running here
                    </td>
                  </tr>
                </tbody>
              </table>

              <div className="stack" style={{ gap: 8, marginTop: 16 }}>
                {cluster.workers.length === 0 && (
                  <p className="muted small" style={{ margin: 0 }}>
                    No peers configured — this node runs as a single-replica cluster.
                  </p>
                )}
                {cluster.workers.map((worker) => (
                  <div
                    key={worker.nodeId}
                    className={`cluster-node ${
                      worker.nodeId === cluster.leaderId ? 'cluster-node-leader' : ''
                    }`}
                  >
                    <span className="mono">{worker.nodeId}</span>
                    <span className="row" style={{ gap: 10 }}>
                      <span className="muted small">
                        {worker.inFlightTasks}/{worker.capacity} busy
                      </span>
                      <span className="muted small">
                        seen {relativeSeconds(worker.lastSeenEpochMillis)}
                      </span>
                      {worker.alive ? (
                        <span className="badge badge-success">alive</span>
                      ) : (
                        <span className="badge badge-danger">
                          dead ({worker.missedHeartbeats} missed)
                        </span>
                      )}
                    </span>
                  </div>
                ))}
              </div>
            </>
          )}
        </div>

        <div className="card">
          <h2 style={{ fontSize: '1.1rem' }}>Recent bookings</h2>
          {bookingsLoading && <Spinner label="Loading bookings…" />}
          {bookings && bookings.length > 0 && (
            <table className="table">
              <thead>
                <tr>
                  <th>Reference</th>
                  <th>Route</th>
                  <th>Departs</th>
                  <th>Seats</th>
                  <th>Amount</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {bookings.slice(0, 25).map((booking) => (
                  <tr key={booking.id}>
                    <td className="mono">{booking.bookingReference}</td>
                    <td>
                      {booking.originCode} → {booking.destinationCode}
                    </td>
                    <td>{formatDateTime(booking.departureTime)}</td>
                    <td>{booking.seats}</td>
                    <td>{formatMoney(booking.totalAmount, booking.currency)}</td>
                    <td>{booking.status}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          {bookings && bookings.length === 0 && (
            <p className="muted small" style={{ margin: 0 }}>
              No bookings yet.
            </p>
          )}
        </div>
      </div>
    </section>
  )
}
