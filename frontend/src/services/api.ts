import axios, { AxiosInstance, AxiosResponse, AxiosError } from 'axios'
import { toast } from 'vue3-toastify'
import router from '@/router'

// Create axios instance
const api: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_URL || 'http://localhost:3000/api/v1',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// Request interceptor
api.interceptors.request.use(
  (config) => {
    // Add auth token if available
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// Response interceptor
api.interceptors.response.use(
  (response: AxiosResponse) => {
    return response
  },
  async (error: AxiosError) => {
    const originalRequest = error.config as any
    
    // Handle 401 Unauthorized
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true
      
      try {
        // Try to refresh token
        const refreshToken = localStorage.getItem('refreshToken')
        if (refreshToken) {
          const response = await axios.post(
            `${import.meta.env.VITE_API_URL || 'http://localhost:3000/api/v1'}/auth/refresh`,
            { refreshToken }
          )
          
          const { token: newToken } = response.data.data
          localStorage.setItem('token', newToken)
          
          // Retry original request
          originalRequest.headers.Authorization = `Bearer ${newToken}`
          return api(originalRequest)
        }
      } catch (refreshError) {
        // Refresh failed, redirect to login
        localStorage.removeItem('token')
        localStorage.removeItem('refreshToken')
        router.push('/login')
        toast.error('Session expired. Please login again.')
      }
    }
    
    // Handle other errors
    if (error.response?.status === 403) {
      toast.error('Access denied. You do not have permission to perform this action.')
    } else if (error.response?.status === 404) {
      toast.error('Resource not found.')
    } else if (error.response?.status === 500) {
      toast.error('Server error. Please try again later.')
    } else if (error.code === 'ECONNABORTED') {
      toast.error('Request timeout. Please check your connection.')
    } else if (!error.response) {
      toast.error('Network error. Please check your connection.')
    }
    
    return Promise.reject(error)
  }
)

// API service methods
export const apiService = {
  // Auth endpoints
  auth: {
    login: (credentials: { email: string; password: string }) =>
      api.post('/auth/login', credentials),
    register: (data: { name: string; email: string; password: string }) =>
      api.post('/auth/register', data),
    logout: () => api.post('/auth/logout'),
    refresh: () => api.post('/auth/refresh'),
    me: () => api.get('/auth/me'),
    updateProfile: (data: any) => api.put('/auth/profile', data),
    changePassword: (data: any) => api.put('/auth/password', data),
    forgotPassword: (email: string) => api.post('/auth/forgot-password', { email }),
    resetPassword: (data: any) => api.post('/auth/reset-password', data),
  },

  // Flight endpoints
  flights: {
    getAll: (params?: any) => api.get('/flights', { params }),
    getById: (id: string) => api.get(`/flights/${id}`),
    create: (data: any) => api.post('/flights', data),
    update: (id: string, data: any) => api.put(`/flights/${id}`, data),
    delete: (id: string) => api.delete(`/flights/${id}`),
    search: (params: any) => api.get('/flights/search', { params }),
  },

  // City endpoints
  cities: {
    getAll: () => api.get('/cities'),
    getById: (id: string) => api.get(`/cities/${id}`),
    create: (data: any) => api.post('/cities', data),
    update: (id: string, data: any) => api.put(`/cities/${id}`, data),
    delete: (id: string) => api.delete(`/cities/${id}`),
  },

  // Airport endpoints
  airports: {
    getAll: () => api.get('/airports'),
    getById: (id: string) => api.get(`/airports/${id}`),
    create: (data: any) => api.post('/airports', data),
    update: (id: string, data: any) => api.put(`/airports/${id}`, data),
    delete: (id: string) => api.delete(`/airports/${id}`),
  },

  // Payment endpoints
  payments: {
    createIntent: (data: any) => api.post('/payments/intent', data),
    confirm: (paymentIntentId: string, data: any) =>
      api.post(`/payments/${paymentIntentId}/confirm`, data),
    getDetails: (paymentIntentId: string) => api.get(`/payments/${paymentIntentId}`),
    refund: (paymentIntentId: string, data: any) =>
      api.post(`/payments/${paymentIntentId}/refund`, data),
    cancel: (paymentIntentId: string, data: any) =>
      api.post(`/payments/${paymentIntentId}/cancel`, data),
    getMethods: () => api.get('/payments/methods'),
  },

  // Customer endpoints
  customers: {
    create: (data: any) => api.post('/customers', data),
    getById: (customerId: string) => api.get(`/customers/${customerId}`),
    getPaymentHistory: (customerId: string, params?: any) =>
      api.get(`/customers/${customerId}/payments`, { params }),
  },

  // Booking endpoints (if implemented in backend)
  bookings: {
    getAll: (params?: any) => api.get('/bookings', { params }),
    getById: (id: string) => api.get(`/bookings/${id}`),
    create: (data: any) => api.post('/bookings', data),
    update: (id: string, data: any) => api.put(`/bookings/${id}`, data),
    delete: (id: string) => api.delete(`/bookings/${id}`),
    getUserBookings: () => api.get('/bookings/my'),
  },

  // User endpoints
  users: {
    getAll: (params?: any) => api.get('/users', { params }),
    getById: (id: string) => api.get(`/users/${id}`),
    create: (data: any) => api.post('/users', data),
    update: (id: string, data: any) => api.put(`/users/${id}`, data),
    delete: (id: string) => api.delete(`/users/${id}`),
  },

  // System endpoints
  system: {
    health: () => api.get('/health'),
    metrics: () => api.get('/metrics'),
  },
}

// Export the axios instance for direct use
export { api }

// Export types
export interface ApiResponse<T = any> {
  success: boolean
  message: string
  data: T
  error?: string
}

export interface PaginatedResponse<T = any> {
  data: T[]
  pagination: {
    page: number
    limit: number
    total: number
    totalPages: number
  }
}

export interface Flight {
  id: string
  flightNumber: string
  departureCity: string
  arrivalCity: string
  departureTime: string
  arrivalTime: string
  price: number
  availableSeats: number
  status: 'scheduled' | 'delayed' | 'cancelled' | 'completed'
  createdAt: string
  updatedAt: string
}

export interface City {
  id: string
  name: string
  country: string
  timezone: string
  createdAt: string
  updatedAt: string
}

export interface Airport {
  id: string
  name: string
  code: string
  city: string
  country: string
  latitude: number
  longitude: number
  createdAt: string
  updatedAt: string
}

export interface PaymentIntent {
  clientSecret: string
  paymentIntentId: string
  amount: number
  currency: string
  status: string
}

export interface Customer {
  customerId: string
  email: string
  name: string
}

export interface Booking {
  id: string
  userId: string
  flightId: string
  passengerCount: number
  totalAmount: number
  status: 'pending' | 'confirmed' | 'cancelled' | 'completed'
  paymentStatus: 'pending' | 'paid' | 'failed' | 'refunded'
  createdAt: string
  updatedAt: string
}
