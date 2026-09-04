import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '@/stores/auth'

interface Props {
  children: ReactNode
  requireAdmin?: boolean
}

/**
 * Client-side gate. It hides UI rather than protecting data - every endpoint behind it is
 * authorized again at the gateway and in the owning service.
 */
export default function ProtectedRoute({ children, requireAdmin = false }: Props) {
  const location = useLocation()
  const token = useAuth((state) => state.token)
  const user = useAuth((state) => state.user)
  const loading = useAuth((state) => state.loading)

  // A stored token with no profile yet means restore() is still running; waiting avoids
  // bouncing a signed-in traveller to the login page on every refresh.
  if (token && !user && loading) {
    return (
      <div className="loading-block">
        <span className="spinner spinner-dark" /> Loading your account…
      </div>
    )
  }

  if (!token || !user) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  }

  if (requireAdmin && user.role !== 'admin') {
    return <Navigate to="/" replace />
  }

  return <>{children}</>
}
