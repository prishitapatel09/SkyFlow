import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import toast from 'react-hot-toast'
import { errorMessage } from '@/lib/api'
import { useAuth } from '@/stores/auth'

interface LocationState {
  from?: string
}

export default function Login() {
  const navigate = useNavigate()
  const location = useLocation()
  const login = useAuth((state) => state.login)
  const loading = useAuth((state) => state.loading)
  const user = useAuth((state) => state.user)

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)

  const from = (location.state as LocationState | null)?.from ?? '/bookings'

  useEffect(() => {
    if (new URLSearchParams(location.search).has('expired')) {
      toast('Your session expired. Please sign in again.', { icon: '🔒' })
    }
  }, [location.search])

  // Already signed in (e.g. arrived here by a stale link): go straight through.
  useEffect(() => {
    if (user) {
      navigate(from, { replace: true })
    }
  }, [user, from, navigate])

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setError(null)
    try {
      await login(email.trim(), password)
      toast.success('Signed in')
      navigate(from, { replace: true })
    } catch (caught) {
      setError(errorMessage(caught, 'Could not sign you in'))
    }
  }

  return (
    <section className="container">
      <form className="card form-narrow" onSubmit={submit}>
        <h1 style={{ fontSize: '1.5rem' }}>Sign in</h1>
        <p className="muted small">Welcome back to SkyFlow.</p>

        <div className="field">
          <label htmlFor="email">Email</label>
          <input
            id="email"
            type="email"
            autoComplete="email"
            required
            value={email}
            onChange={(event) => setEmail(event.target.value)}
          />
        </div>

        <div className="field">
          <label htmlFor="password">Password</label>
          <input
            id="password"
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
        </div>

        {error && <p className="field-error">{error}</p>}

        <button type="submit" className="btn btn-block" disabled={loading}>
          {loading ? <span className="spinner" /> : 'Sign in'}
        </button>

        <p className="small muted" style={{ marginTop: 16, marginBottom: 0 }}>
          New here? <Link to="/register">Create an account</Link>
        </p>
      </form>
    </section>
  )
}
