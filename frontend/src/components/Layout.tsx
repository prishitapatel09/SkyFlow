import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import AssistantWidget from './AssistantWidget'
import { useAuth } from '@/stores/auth'

export default function Layout() {
  const navigate = useNavigate()
  const user = useAuth((state) => state.user)
  const logout = useAuth((state) => state.logout)
  const isAdmin = useAuth((state) => state.isAdmin)()

  function handleLogout() {
    logout()
    navigate('/')
  }

  return (
    <div className="app-shell">
      <header className="site-header">
        <div className="container">
          <Link to="/" className="brand">
            <span className="brand-mark" aria-hidden="true">
              ✈
            </span>
            SkyFlow
          </Link>

          <nav className="nav">
            <NavLink to="/search">Search</NavLink>
            {user && <NavLink to="/bookings">My bookings</NavLink>}
            {isAdmin && <NavLink to="/admin">Admin</NavLink>}
            {user ? (
              <>
                <NavLink to="/profile">{user.name.split(' ')[0]}</NavLink>
                <button type="button" className="btn btn-secondary btn-sm" onClick={handleLogout}>
                  Sign out
                </button>
              </>
            ) : (
              <>
                <NavLink to="/login">Sign in</NavLink>
                <Link to="/register" className="btn btn-sm">
                  Get started
                </Link>
              </>
            )}
          </nav>
        </div>
      </header>

      <main className="app-main">
        <Outlet />
      </main>

      <footer className="site-footer">
        <div className="container spread">
          <span>SkyFlow — Spring Boot microservices, React, Redis, RabbitMQ, gRPC.</span>
          <span>Times shown in UTC.</span>
        </div>
      </footer>

      {/* Only offered to signed-in travellers: its tools read their bookings. */}
      {user && <AssistantWidget />}
    </div>
  )
}
