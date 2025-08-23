import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { api } from '@/services/api'
import { toast } from 'vue3-toastify'

export interface User {
  id: string
  email: string
  name: string
  role: 'user' | 'admin'
  createdAt: string
  updatedAt: string
}

export interface LoginCredentials {
  email: string
  password: string
}

export interface RegisterData {
  name: string
  email: string
  password: string
  confirmPassword: string
}

export const useAuthStore = defineStore('auth', () => {
  // State
  const user = ref<User | null>(null)
  const token = ref<string | null>(localStorage.getItem('token'))
  const loading = ref(false)

  // Getters
  const isAuthenticated = computed(() => !!token.value && !!user.value)
  const isAdmin = computed(() => user.value?.role === 'admin')

  // Actions
  const login = async (credentials: LoginCredentials) => {
    try {
      loading.value = true
      const response = await api.post('/auth/login', credentials)
      
      const { token: authToken, user: userData } = response.data.data
      
      // Store token
      token.value = authToken
      localStorage.setItem('token', authToken)
      
      // Store user data
      user.value = userData
      
      // Set auth header for future requests
      api.defaults.headers.common['Authorization'] = `Bearer ${authToken}`
      
      toast.success('Login successful!')
      return { success: true }
    } catch (error: any) {
      const message = error.response?.data?.message || 'Login failed'
      toast.error(message)
      return { success: false, error: message }
    } finally {
      loading.value = false
    }
  }

  const register = async (data: RegisterData) => {
    try {
      loading.value = true
      const response = await api.post('/auth/register', {
        name: data.name,
        email: data.email,
        password: data.password
      })
      
      const { token: authToken, user: userData } = response.data.data
      
      // Store token
      token.value = authToken
      localStorage.setItem('token', authToken)
      
      // Store user data
      user.value = userData
      
      // Set auth header for future requests
      api.defaults.headers.common['Authorization'] = `Bearer ${authToken}`
      
      toast.success('Registration successful!')
      return { success: true }
    } catch (error: any) {
      const message = error.response?.data?.message || 'Registration failed'
      toast.error(message)
      return { success: false, error: message }
    } finally {
      loading.value = false
    }
  }

  const logout = () => {
    // Clear token
    token.value = null
    localStorage.removeItem('token')
    
    // Clear user data
    user.value = null
    
    // Remove auth header
    delete api.defaults.headers.common['Authorization']
    
    toast.success('Logged out successfully')
  }

  const refreshToken = async () => {
    try {
      const response = await api.post('/auth/refresh')
      const { token: newToken } = response.data.data
      
      token.value = newToken
      localStorage.setItem('token', newToken)
      api.defaults.headers.common['Authorization'] = `Bearer ${newToken}`
      
      return true
    } catch (error) {
      logout()
      return false
    }
  }

  const checkAuth = async () => {
    if (!token.value) return false
    
    try {
      // Set auth header
      api.defaults.headers.common['Authorization'] = `Bearer ${token.value}`
      
      // Verify token by fetching user data
      const response = await api.get('/auth/me')
      user.value = response.data.data
      
      return true
    } catch (error) {
      // Token is invalid, try to refresh
      const refreshed = await refreshToken()
      if (!refreshed) {
        logout()
        return false
      }
      return true
    }
  }

  const updateProfile = async (profileData: Partial<User>) => {
    try {
      loading.value = true
      const response = await api.put('/auth/profile', profileData)
      user.value = response.data.data
      
      toast.success('Profile updated successfully!')
      return { success: true }
    } catch (error: any) {
      const message = error.response?.data?.message || 'Profile update failed'
      toast.error(message)
      return { success: false, error: message }
    } finally {
      loading.value = false
    }
  }

  const changePassword = async (passwordData: {
    currentPassword: string
    newPassword: string
    confirmPassword: string
  }) => {
    try {
      loading.value = true
      await api.put('/auth/password', passwordData)
      
      toast.success('Password changed successfully!')
      return { success: true }
    } catch (error: any) {
      const message = error.response?.data?.message || 'Password change failed'
      toast.error(message)
      return { success: false, error: message }
    } finally {
      loading.value = false
    }
  }

  const forgotPassword = async (email: string) => {
    try {
      loading.value = true
      await api.post('/auth/forgot-password', { email })
      
      toast.success('Password reset email sent!')
      return { success: true }
    } catch (error: any) {
      const message = error.response?.data?.message || 'Failed to send reset email'
      toast.error(message)
      return { success: false, error: message }
    } finally {
      loading.value = false
    }
  }

  const resetPassword = async (resetData: {
    token: string
    password: string
    confirmPassword: string
  }) => {
    try {
      loading.value = true
      await api.post('/auth/reset-password', resetData)
      
      toast.success('Password reset successfully!')
      return { success: true }
    } catch (error: any) {
      const message = error.response?.data?.message || 'Password reset failed'
      toast.error(message)
      return { success: false, error: message }
    } finally {
      loading.value = false
    }
  }

  // Initialize auth state
  const init = async () => {
    if (token.value) {
      await checkAuth()
    }
  }

  return {
    // State
    user,
    token,
    loading,
    
    // Getters
    isAuthenticated,
    isAdmin,
    
    // Actions
    login,
    register,
    logout,
    refreshToken,
    checkAuth,
    updateProfile,
    changePassword,
    forgotPassword,
    resetPassword,
    init
  }
})
