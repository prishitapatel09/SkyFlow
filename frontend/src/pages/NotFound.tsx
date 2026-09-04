import { Link } from 'react-router-dom'

export default function NotFound() {
  return (
    <section className="section">
      <div className="container card empty-state">
        <h1 style={{ fontSize: '2rem' }}>Page not found</h1>
        <p>That route does not exist. It happens.</p>
        <Link to="/" className="btn">
          Back to search
        </Link>
      </div>
    </section>
  )
}
