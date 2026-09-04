import axios, { AxiosError, type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'
import toast from 'react-hot-toast'
import type {
  Airport,
  ApiResponse,
  AuthPayload,
  Booking,
  BookingCreated,
  ChatReply,
  ClusterStatus,
  Flight,
  FlightSearchParams,
  NlSearchResult,
  Page,
  SeatAvailability,
  User,
} from './types'

/** Same-origin by default: the dev server proxies /api and nginx serves it in production. */
const baseURL = import.meta.env.VITE_API_BASE_URL || ''

export const TOKEN_KEY = 'skyflow.token'
export const REFRESH_TOKEN_KEY = 'skyflow.refreshToken'

const http: AxiosInstance = axios.create({
  baseURL,
  timeout: 15_000,
  headers: { 'Content-Type': 'application/json' },
})

/** The assistant calls Claude, which is slower than everything else here. */
const AI_TIMEOUT_MS = 120_000

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

type RetriableConfig = InternalAxiosRequestConfig & { _retried?: boolean }

/**
 * Refresh-on-401, with a single in-flight refresh shared by every queued request - otherwise a
 * page that fires five calls at once triggers five refreshes and four of them lose the race.
 */
let refreshInFlight: Promise<string> | null = null

async function refreshAccessToken(): Promise<string> {
  const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY)
  if (!refreshToken) {
    throw new Error('No refresh token')
  }
  const response = await axios.post<ApiResponse<AuthPayload>>(
    `${baseURL}/api/v1/auth/refresh`,
    { refreshToken },
    { headers: { 'Content-Type': 'application/json' } },
  )
  const payload = response.data.data
  localStorage.setItem(TOKEN_KEY, payload.token)
  localStorage.setItem(REFRESH_TOKEN_KEY, payload.refreshToken)
  return payload.token
}

http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiResponse<unknown>>) => {
    const config = error.config as RetriableConfig | undefined
    const status = error.response?.status

    if (status === 401 && config && !config._retried && localStorage.getItem(REFRESH_TOKEN_KEY)) {
      config._retried = true
      try {
        refreshInFlight ??= refreshAccessToken().finally(() => {
          refreshInFlight = null
        })
        const token = await refreshInFlight
        config.headers.Authorization = `Bearer ${token}`
        return http(config)
      } catch {
        localStorage.removeItem(TOKEN_KEY)
        localStorage.removeItem(REFRESH_TOKEN_KEY)
        // A full navigation rather than a router push: the auth store lives outside React here.
        if (!window.location.pathname.startsWith('/login')) {
          window.location.assign('/login?expired=1')
        }
      }
    }

    if (status === 429) {
      toast.error('Too many requests. Give it a moment and try again.')
    } else if (status === 503 || (status && status >= 500)) {
      toast.error(error.response?.data?.message ?? 'Something went wrong on our side.')
    } else if (!error.response) {
      toast.error('Network error. Check your connection.')
    }

    return Promise.reject(error)
  },
)

/** Unwraps the shared response envelope so callers deal in domain types. */
async function unwrap<T>(promise: Promise<{ data: ApiResponse<T> }>): Promise<T> {
  const response = await promise
  return response.data.data
}

/** Turns an axios failure into the server's own message, which is written for humans. */
export function errorMessage(error: unknown, fallback = 'Something went wrong'): string {
  if (axios.isAxiosError(error)) {
    const payload = error.response?.data as ApiResponse<unknown> | undefined
    if (payload?.message) {
      return payload.message
    }
  }
  return fallback
}

export const api = {
  auth: {
    login: (email: string, password: string) =>
      unwrap<AuthPayload>(http.post('/api/v1/auth/login', { email, password })),
    register: (name: string, email: string, password: string) =>
      unwrap<AuthPayload>(http.post('/api/v1/auth/register', { name, email, password })),
    logout: () => http.post('/api/v1/auth/logout').catch(() => undefined),
    me: () => unwrap<User>(http.get('/api/v1/auth/me')),
    updateProfile: (payload: Partial<Pick<User, 'name' | 'email' | 'phone'>>) =>
      unwrap<User>(http.put('/api/v1/auth/profile', payload)),
    changePassword: (currentPassword: string, newPassword: string) =>
      unwrap<void>(http.put('/api/v1/auth/password', { currentPassword, newPassword })),
  },

  flights: {
    search: (params: FlightSearchParams) =>
      unwrap<Page<Flight>>(http.get('/api/v1/flights/search', { params })),
    get: (id: number | string) => unwrap<Flight>(http.get(`/api/v1/flights/${id}`)),
    seats: (id: number | string) =>
      unwrap<SeatAvailability>(http.get(`/api/v1/flights/${id}/seats`)),
  },

  airports: {
    list: () => unwrap<Airport[]>(http.get('/api/v1/airports')),
  },

  bookings: {
    create: (flightId: number, passengers: { fullName: string; passportNumber?: string }[],
             contactEmail?: string, contactName?: string) =>
      unwrap<BookingCreated>(http.post('/api/v1/bookings', {
        flightId,
        passengers,
        contactEmail,
        contactName,
      })),
    mine: () => unwrap<Booking[]>(http.get('/api/v1/bookings/my')),
    cancel: (id: number | string, reason?: string) =>
      unwrap<Booking>(http.post(`/api/v1/bookings/${id}/cancel`, { reason })),
    all: (status?: string) =>
      unwrap<Booking[]>(http.get('/api/v1/bookings', { params: status ? { status } : undefined })),
  },

  payments: {
    sync: (paymentIntentId: string) =>
      unwrap<{ status: string; bookingId?: string }>(
        http.post(`/api/v1/payments/${paymentIntentId}/sync`),
      ),
  },

  ai: {
    search: (query: string) =>
      unwrap<NlSearchResult>(
        http.post('/api/v1/ai/search', { query }, { timeout: AI_TIMEOUT_MS }),
      ),
    chat: (message: string, conversationId?: string) =>
      unwrap<ChatReply>(
        http.post('/api/v1/ai/chat', { message, conversationId }, { timeout: AI_TIMEOUT_MS }),
      ),
    clear: (conversationId: string) => http.delete(`/api/v1/ai/chat/${conversationId}`),
  },

  cluster: {
    status: () => unwrap<ClusterStatus>(http.get('/api/v1/cluster/status')),
  },
}

export { http }
