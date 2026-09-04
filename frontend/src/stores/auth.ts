import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import { REFRESH_TOKEN_KEY, TOKEN_KEY, api } from '@/lib/api'
import type { AuthPayload, User } from '@/lib/types'

interface AuthState {
  user: User | null
  token: string | null
  loading: boolean
  isAuthenticated: () => boolean
  isAdmin: () => boolean
  login: (email: string, password: string) => Promise<void>
  register: (name: string, email: string, password: string) => Promise<void>
  logout: () => void
  /** Re-reads the account on a page load so a stale cached user is corrected. */
  restore: () => Promise<void>
  setUser: (user: User) => void
}

/**
 * Tokens live in localStorage because the axios interceptor - which is not a React component -
 * has to read them on every request. Only the user profile is persisted through zustand.
 */
function storeTokens(payload: AuthPayload) {
  localStorage.setItem(TOKEN_KEY, payload.token)
  localStorage.setItem(REFRESH_TOKEN_KEY, payload.refreshToken)
}

export const useAuth = create<AuthState>()(
  persist(
    (set, get) => ({
      user: null,
      token: localStorage.getItem(TOKEN_KEY),
      loading: false,

      isAuthenticated: () => Boolean(get().token && get().user),
      isAdmin: () => get().user?.role === 'admin',

      login: async (email, password) => {
        set({ loading: true })
        try {
          const payload = await api.auth.login(email, password)
          storeTokens(payload)
          set({ user: payload.user, token: payload.token })
        } finally {
          set({ loading: false })
        }
      },

      register: async (name, email, password) => {
        set({ loading: true })
        try {
          const payload = await api.auth.register(name, email, password)
          storeTokens(payload)
          set({ user: payload.user, token: payload.token })
        } finally {
          set({ loading: false })
        }
      },

      logout: () => {
        void api.auth.logout()
        localStorage.removeItem(TOKEN_KEY)
        localStorage.removeItem(REFRESH_TOKEN_KEY)
        set({ user: null, token: null })
      },

      restore: async () => {
        const token = localStorage.getItem(TOKEN_KEY)
        if (!token) {
          set({ user: null, token: null })
          return
        }
        set({ loading: true, token })
        try {
          set({ user: await api.auth.me() })
        } catch {
          localStorage.removeItem(TOKEN_KEY)
          localStorage.removeItem(REFRESH_TOKEN_KEY)
          set({ user: null, token: null })
        } finally {
          set({ loading: false })
        }
      },

      setUser: (user) => set({ user }),
    }),
    {
      name: 'skyflow.auth',
      partialize: (state) => ({ user: state.user }),
    },
  ),
)
