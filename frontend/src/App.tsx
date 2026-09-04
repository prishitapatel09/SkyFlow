import { useEffect } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import Layout from '@/components/Layout'
import ProtectedRoute from '@/components/ProtectedRoute'
import Home from '@/pages/Home'
import FlightSearch from '@/pages/FlightSearch'
import FlightDetail from '@/pages/FlightDetail'
import Login from '@/pages/Login'
import Register from '@/pages/Register'
import Checkout from '@/pages/Checkout'
import PaymentResult from '@/pages/PaymentResult'
import MyBookings from '@/pages/MyBookings'
import Profile from '@/pages/Profile'
import AdminDashboard from '@/pages/AdminDashboard'
import NotFound from '@/pages/NotFound'
import { useAuth } from '@/stores/auth'

export default function App() {
  const restore = useAuth((state) => state.restore)

  // One profile fetch on load: it also verifies the stored token is still good.
  useEffect(() => {
    void restore()
  }, [restore])

  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<Home />} />
        <Route path="/search" element={<FlightSearch />} />
        <Route path="/flights" element={<Navigate to="/search" replace />} />
        <Route path="/flights/:id" element={<FlightDetail />} />
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />

        <Route
          path="/checkout"
          element={
            <ProtectedRoute>
              <Checkout />
            </ProtectedRoute>
          }
        />
        <Route
          path="/payment/:paymentIntentId"
          element={
            <ProtectedRoute>
              <PaymentResult />
            </ProtectedRoute>
          }
        />
        <Route
          path="/bookings"
          element={
            <ProtectedRoute>
              <MyBookings />
            </ProtectedRoute>
          }
        />
        <Route
          path="/profile"
          element={
            <ProtectedRoute>
              <Profile />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin"
          element={
            <ProtectedRoute requireAdmin>
              <AdminDashboard />
            </ProtectedRoute>
          }
        />

        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  )
}
