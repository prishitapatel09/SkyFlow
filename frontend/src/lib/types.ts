/** Payload shapes returned by the SkyFlow services. */

export interface ApiResponse<T> {
  success: boolean
  message: string
  data: T
  err?: unknown
  timestamp: string
}

export interface Page<T> {
  items: T[]
  pagination: {
    page: number
    size: number
    total: number
    totalPages: number
  }
}

export interface City {
  id: number
  name: string
  countryCode: string
}

export interface Airport {
  id: number
  name: string
  code: string
  address?: string
  city?: City
}

export interface Airplane {
  id: number
  modelNumber: string
  capacity: number
}

export interface Flight {
  id: number
  flightNumber: string
  departureAirport: Airport
  arrivalAirport: Airport
  airplane?: Airplane
  departureTime: string
  arrivalTime: string
  durationMinutes: number
  price: number
  boardingGate?: string
  totalSeats: number
  availableSeats: number
  status: 'scheduled' | 'delayed' | 'cancelled' | 'completed'
}

export interface SeatAvailability {
  flightId: number
  totalSeats: number
  availableSeats: number
}

export type BookingStatus =
  | 'PENDING_PAYMENT'
  | 'CONFIRMED'
  | 'EXPIRED'
  | 'CANCELLED'
  | 'FAILED'

export interface Passenger {
  id: number
  fullName: string
  seatNumber?: string
  passportNumber?: string
}

export interface Booking {
  id: number
  bookingReference: string
  userId: string
  contactEmail: string
  contactName?: string
  flightId: number
  flightNumber: string
  originCode: string
  destinationCode: string
  departureTime: string
  arrivalTime: string
  seats: number
  totalAmount: number
  currency: string
  status: BookingStatus
  paymentIntentId?: string
  holdExpiresAt?: string
  passengers: Passenger[]
  createdAt: string
  updatedAt: string
}

export interface PaymentIntent {
  paymentIntentId: string
  clientSecret: string
  amount: number
  currency: string
  status: string
}

export interface BookingCreated {
  booking: Booking
  payment: PaymentIntent
}

export interface User {
  id: number
  name: string
  email: string
  role: 'user' | 'admin'
  phone?: string
  createdAt: string
  updatedAt: string
}

export interface AuthPayload {
  token: string
  refreshToken: string
  expiresIn: number
  user: User
}

/** What the natural language search understood, so the UI can show and correct it. */
export interface SearchCriteria {
  originCity?: string
  originAirportCode?: string
  destinationCity?: string
  destinationAirportCode?: string
  departureDate?: string
  passengers?: number
  maxPrice?: number
  sortBy?: string
  interpretation?: string
}

export interface NlSearchResult {
  criteria: SearchCriteria
  flights: Flight[]
  totalResults: number
}

export interface ChatReply {
  conversationId: string
  reply: string
  toolsUsed: string[]
}

/** Coordination state of the booking-service replicas. */
export interface ClusterStatus {
  nodeId: string
  role: 'FOLLOWER' | 'CANDIDATE' | 'LEADER'
  term: number
  leaderId?: string
  quorum: number
  pendingTasks: number
  inFlightTasks: number
  localInFlightTasks: number
  lastTaskIndex: number
  workers: ClusterWorker[]
}

export interface ClusterWorker {
  nodeId: string
  alive: boolean
  inFlightTasks: number
  capacity: number
  missedHeartbeats: number
  lastSeenEpochMillis?: number
}

export interface FlightSearchParams {
  departureAirportCode?: string
  arrivalAirportCode?: string
  departureCity?: string
  arrivalCity?: string
  departureDate?: string
  minPrice?: number
  maxPrice?: number
  passengers?: number
  sortBy?: string
  page?: number
  size?: number
}
