import { useQuery } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import { api } from '@/lib/api'
import FlightCard from '@/components/FlightCard'
import Spinner from '@/components/Spinner'
import type { FlightSearchParams } from '@/lib/types'

const SORT_OPTIONS = [
  { value: 'departureTime', label: 'Departure time' },
  { value: 'price', label: 'Price: low to high' },
  { value: '-price', label: 'Price: high to low' },
  { value: 'duration', label: 'Duration' },
]

export default function FlightSearch() {
  // The URL is the source of truth for a search, so results are shareable and survive a reload.
  const [params, setParams] = useSearchParams()

  const passengers = Number(params.get('passengers') ?? 1)
  const query: FlightSearchParams = {
    departureAirportCode: params.get('from') ?? undefined,
    arrivalAirportCode: params.get('to') ?? undefined,
    departureDate: params.get('date') ?? undefined,
    maxPrice: params.get('maxPrice') ? Number(params.get('maxPrice')) : undefined,
    passengers,
    sortBy: params.get('sort') ?? 'departureTime',
    page: Number(params.get('page') ?? 0),
    size: 20,
  }

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['flights', query],
    queryFn: () => api.flights.search(query),
  })

  const { data: airports = [] } = useQuery({
    queryKey: ['airports'],
    queryFn: api.airports.list,
    staleTime: 60 * 60 * 1000,
  })

  function update(key: string, value: string) {
    const next = new URLSearchParams(params)
    if (value) {
      next.set(key, value)
    } else {
      next.delete(key)
    }
    // Any filter change resets paging; page 3 of a different search is meaningless.
    next.delete('page')
    setParams(next)
  }

  const pagination = data?.pagination
  const page = pagination?.page ?? 0
  const totalPages = pagination?.totalPages ?? 0

  return (
    <section className="section">
      <div className="container stack">
        <div className="spread">
          <div>
            <h1 className="section-title" style={{ marginBottom: 4 }}>
              Flights
            </h1>
            <p className="muted small" style={{ margin: 0 }}>
              {pagination
                ? `${pagination.total} flight${pagination.total === 1 ? '' : 's'} with room for ${passengers}`
                : 'Searching…'}
            </p>
          </div>

          <div className="row">
            <div>
              <label htmlFor="sort">Sort</label>
              <select
                id="sort"
                value={params.get('sort') ?? 'departureTime'}
                onChange={(event) => update('sort', event.target.value)}
              >
                {SORT_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </div>
          </div>
        </div>

        <div className="card">
          <div className="search-grid">
            <div>
              <label htmlFor="from">From</label>
              <select
                id="from"
                value={params.get('from') ?? ''}
                onChange={(event) => update('from', event.target.value)}
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
              <label htmlFor="to">To</label>
              <select
                id="to"
                value={params.get('to') ?? ''}
                onChange={(event) => update('to', event.target.value)}
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
                value={params.get('date') ?? ''}
                onChange={(event) => update('date', event.target.value)}
              />
            </div>

            <div>
              <label htmlFor="maxPrice">Max price</label>
              <input
                id="maxPrice"
                type="number"
                min={0}
                step={25}
                placeholder="Any"
                value={params.get('maxPrice') ?? ''}
                onChange={(event) => update('maxPrice', event.target.value)}
              />
            </div>

            <div>
              <label htmlFor="passengers">Travellers</label>
              <select
                id="passengers"
                value={passengers}
                onChange={(event) => update('passengers', event.target.value)}
              >
                {Array.from({ length: 9 }, (_, index) => index + 1).map((count) => (
                  <option key={count} value={count}>
                    {count}
                  </option>
                ))}
              </select>
            </div>
          </div>
        </div>

        {isLoading && <Spinner label="Finding flights…" />}

        {isError && (
          <div className="card empty-state">
            <p>We could not load flights just now.</p>
            <button type="button" className="btn btn-secondary" onClick={() => void refetch()}>
              Try again
            </button>
          </div>
        )}

        {data && data.items.length === 0 && (
          <div className="card empty-state">
            <h3>No flights match those filters</h3>
            <p>Try a different date, a nearby airport, or fewer travellers.</p>
          </div>
        )}

        {data && data.items.length > 0 && (
          <div className="card card-flush">
            {data.items.map((flight) => (
              <FlightCard key={flight.id} flight={flight} passengers={passengers} />
            ))}
          </div>
        )}

        {totalPages > 1 && (
          <div className="row" style={{ justifyContent: 'center' }}>
            <button
              type="button"
              className="btn btn-secondary btn-sm"
              disabled={page === 0}
              onClick={() => update('page', String(page - 1))}
            >
              Previous
            </button>
            <span className="small muted">
              Page {page + 1} of {totalPages}
            </span>
            <button
              type="button"
              className="btn btn-secondary btn-sm"
              disabled={page + 1 >= totalPages}
              onClick={() => update('page', String(page + 1))}
            >
              Next
            </button>
          </div>
        )}
      </div>
    </section>
  )
}
