import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import toast from 'react-hot-toast'
import { errorMessage } from '@/lib/api'
import { useAuth } from '@/stores/auth'

export default function Register() {
  const navigate = useNavigate()
  const register = useAuth((state) => state.register)
  const loading = useAuth((state) => state.loading)

  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setError(null)

    if (password !== confirm) {
      setError('Passwords do not match')
      return
    }
    if (password.length < 8) {
      setError('Password must be at least 8 characters')
      return
    }

    try {
      await register(name.trim(), email.trim(), password)
      toast.success('Account created')
      navigate('/bookings', { replace: true })
    } catch (caught) {
      setError(errorMessage(caught, 'Could not create your account'))
    }
  }

  return (
    <section className="container">
      <form className="card form-narrow" onSubmit={submit}>
        <h1 style={{ fontSize: '1.5rem' }}>Create an account</h1>
        <p className="muted small">You need one to book and to use the assistant.</p>

        <div className="field">
          <label htmlFor="name">Full name</label>
          <input
            id="name"
            required
            autoComplete="name"
            value={name}
            onChange={(event) => setName(event.target.value)}
          />
        </div>

        <div className="field">
          <label htmlFor="email">Email</label>
          <input
            id="email"
            type="email"
            required
            autoComplete="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
          />
        </div>

        <div className="field">
          <label htmlFor="password">Password</label>
          <input
            id="password"
            type="password"
            required
            minLength={8}
            autoComplete="new-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
        </div>

        <div className="field">
          <label htmlFor="confirm">Confirm password</label>
          <input
            id="confirm"
            type="password"
            required
            autoComplete="new-password"
            value={confirm}
            onChange={(event) => setConfirm(event.target.value)}
          />
        </div>

        {error && <p className="field-error">{error}</p>}

        <button type="submit" className="btn btn-block" disabled={loading}>
          {loading ? <span className="spinner" /> : 'Create account'}
        </button>

        <p className="small muted" style={{ marginTop: 16, marginBottom: 0 }}>
          Already have an account? <Link to="/login">Sign in</Link>
        </p>
      </form>
    </section>
  )
}
