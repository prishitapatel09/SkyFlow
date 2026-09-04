import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { api, errorMessage } from '@/lib/api'
import { todayIso } from '@/lib/format'
import FlightCard from '@/components/FlightCard'
import type { NlSearchResult } from '@/lib/types'

const FEATURES = [
  {
    title: 'Search that stays fast',
    body:
      'Flight search and seat availability are served from Redis, so a repeated route and date ' +
      'never waits on the database.',
  },
  {
    title: 'Ask in plain English',
    body:
      '"Cheap morning flight from Boston to SFO next Friday for two" turns into a real search, ' +
      'with the interpretation shown so you can correct it.',
  },
  {
    title: 'Seats that cannot oversell',
    body:
      'Every booking reserves its seats in a single conditional update, so two travellers cannot ' +
      'both take the last one.',
  },
  {
    title: 'Email that never blocks checkout',
    body:
      'Confirmations and reminders are published to RabbitMQ and delivered by a separate service.',
  },
]

const STATS = [
  { value: '75%', label: 'lower search latency with caching' },
  { value: '7', label: 'Spring Boot services' },
  { value: '3', label: 'replicas electing one master' },
  { value: '0', label: 'oversold seats' },
]

export default function Home() {
  const navigate = useNavigate()
  const [prompt, setPrompt] = useState('')
  const [aiResult, setAiResult] = useState<NlSearchResult | null>(null)
  const [asking, setAsking] = useState(false)

  const [origin, setOrigin] = useState('')
  const [destination, setDestination] = useState('')
  const [date, setDate] = useState(todayIso())
  const [passengers, setPassengers] = useState(1)

  const { data: airports = [] } = useQuery({
    queryKey: ['airports'],
    queryFn: api.airports.list,
    staleTime: 60 * 60 * 1000,
  })

  async function askAssistant(event: React.FormEvent) {
    event.preventDefault()
    const query = prompt.trim()
    if (!query) {
      return
    }
    setAsking(true)
    try {
      const result = await api.ai.search(query)
      setAiResult(result)
      if (result.criteria.passengers) {
        setPassengers(result.criteria.passengers)
      }
      if (result.totalResults === 0) {
        toast('Nothing matched that. Try a different date or route.', { icon: '🔎' })
      }
    } catch (error) {
      toast.error(errorMessage(error, 'Could not interpret that search.'))
    } finally {
      setAsking(false)
    }
  }

  function submitSearch(event: React.FormEvent) {
    event.preventDefault()
    const params = new URLSearchParams()
    if (origin) params.set('from', origin)
    if (destination) params.set('to', destination)
    if (date) params.set('date', date)
    params.set('passengers', String(passengers))
    navigate(`/search?${params.toString()}`)
  }

  return (
    <>
      <section className="hero">
        <div className="container">
          <h1>
            Fly with <span>SkyFlow</span>
          </h1>
          <p>
            Search live availability, book in a couple of clicks, and ask the assistant anything
            about your trip.
          </p>
          <div className="row">
            <Link to="/search" className="btn btn-secondary">
              Browse all flights
            </Link>
          </div>
        </div>
      </section>

      <div className="container">
        <div className="search-panel">
          <form onSubmit={askAssistant} className="ai-search">
            <input
              value={prompt}
              onChange={(event) => setPrompt(event.target.value)}
              placeholder="Try: cheap flight from Boston to SFO next Friday for two"
              maxLength={500}
              aria-label="Describe your trip"
            />
            <button type="submit" className="btn" disabled={asking || !prompt.trim()}>
              {asking ? <span className="spinner" /> : 'Ask'}
            </button>
          </form>

          {aiResult?.criteria.interpretation && (
            <p className="ai-interpretation">{aiResult.criteria.interpretation}</p>
          )}

          <form onSubmit={submitSearch} className="search-grid">
            <div>
              <label htmlFor="from">From</label>
              <select id="from" value={origin} onChange={(event) => setOrigin(event.target.value)}>
                <option value="">Any airport</option>
                {airports.map((airport) => (
                  <option key={airport.id} value={airport.code}>
                    {airport.code} — {airport.city?.name ?? airport.name}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label htmlFor="to">To</label>
              <select
                id="to"
                value={destination}
                onChange={(event) => setDestination(event.target.value)}
              >
                <option value="">Any airport</option>
                {airports.map((airport) => (
                  <option key={airport.id} value={airport.code}>
                    {airport.code} — {airport.city?.name ?? airport.name}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label htmlFor="date">Departing</label>
              <input
                id="date"
                type="date"
                value={date}
                min={todayIso()}
                onChange={(event) => setDate(event.target.value)}
              />
            </div>

            <div>
              <label htmlFor="passengers">Travellers</label>
              <select
                id="passengers"
                value={passengers}
                onChange={(event) => setPassengers(Number(event.target.value))}
              >
                {Array.from({ length: 9 }, (_, index) => index + 1).map((count) => (
                  <option key={count} value={count}>
                    {count} {count === 1 ? 'traveller' : 'travellers'}
                  </option>
                ))}
              </select>
            </div>

            <button type="submit" className="btn">
              Search flights
            </button>
          </form>
        </div>
      </div>

      {aiResult && aiResult.flights.length > 0 && (
        <section className="section">
          <div className="container">
            <h2 className="section-title">
              {aiResult.totalResults} flight{aiResult.totalResults === 1 ? '' : 's'} found
            </h2>
            <p className="section-subtitle">From your description above.</p>
            <div className="card card-flush">
              {aiResult.flights.map((flight) => (
                <FlightCard key={flight.id} flight={flight} passengers={passengers} />
              ))}
            </div>
          </div>
        </section>
      )}

      <section className="section">
        <div className="container">
          <h2 className="section-title">Built to hold up under load</h2>
          <p className="section-subtitle">
            Seven Spring Boot services behind one gateway, coordinating over gRPC.
          </p>
          <div className="grid grid-2">
            {FEATURES.map((feature) => (
              <article key={feature.title} className="card">
                <h3 style={{ fontSize: '1.05rem' }}>{feature.title}</h3>
                <p className="muted small" style={{ margin: 0 }}>
                  {feature.body}
                </p>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section className="section" style={{ background: 'var(--surface)' }}>
        <div className="container">
          <div className="grid grid-4">
            {STATS.map((stat) => (
              <div key={stat.label} className="stat">
                <div className="stat-value">{stat.value}</div>
                <div className="stat-label">{stat.label}</div>
              </div>
            ))}
          </div>
        </div>
      </section>
    </>
  )
}
