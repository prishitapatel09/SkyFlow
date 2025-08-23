import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'Home',
      component: () => import('@/views/Home.vue'),
      meta: { title: 'SkyFlow - Home' }
    },
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/auth/Login.vue'),
      meta: { title: 'Login - SkyFlow', guest: true }
    },
    {
      path: '/register',
      name: 'Register',
      component: () => import('@/views/auth/Register.vue'),
      meta: { title: 'Register - SkyFlow', guest: true }
    },
    {
      path: '/dashboard',
      name: 'Dashboard',
      component: () => import('@/views/Dashboard.vue'),
      meta: { title: 'Dashboard - SkyFlow', requiresAuth: true }
    },
    {
      path: '/flights',
      name: 'Flights',
      component: () => import('@/views/flights/FlightsList.vue'),
      meta: { title: 'Flights - SkyFlow' }
    },
    {
      path: '/flights/:id',
      name: 'FlightDetail',
      component: () => import('@/views/flights/FlightDetail.vue'),
      meta: { title: 'Flight Details - SkyFlow' }
    },
    {
      path: '/search',
      name: 'FlightSearch',
      component: () => import('@/views/flights/FlightSearch.vue'),
      meta: { title: 'Search Flights - SkyFlow' }
    },
    {
      path: '/booking',
      name: 'Booking',
      component: () => import('@/views/booking/BookingForm.vue'),
      meta: { title: 'Book Flight - SkyFlow', requiresAuth: true }
    },
    {
      path: '/booking/:id',
      name: 'BookingDetail',
      component: () => import('@/views/booking/BookingDetail.vue'),
      meta: { title: 'Booking Details - SkyFlow', requiresAuth: true }
    },
    {
      path: '/payment',
      name: 'Payment',
      component: () => import('@/views/payment/PaymentForm.vue'),
      meta: { title: 'Payment - SkyFlow', requiresAuth: true }
    },
    {
      path: '/payment/success',
      name: 'PaymentSuccess',
      component: () => import('@/views/payment/PaymentSuccess.vue'),
      meta: { title: 'Payment Success - SkyFlow' }
    },
    {
      path: '/payment/failed',
      name: 'PaymentFailed',
      component: () => import('@/views/payment/PaymentFailed.vue'),
      meta: { title: 'Payment Failed - SkyFlow' }
    },
    {
      path: '/profile',
      name: 'Profile',
      component: () => import('@/views/user/Profile.vue'),
      meta: { title: 'Profile - SkyFlow', requiresAuth: true }
    },
    {
      path: '/bookings',
      name: 'MyBookings',
      component: () => import('@/views/user/MyBookings.vue'),
      meta: { title: 'My Bookings - SkyFlow', requiresAuth: true }
    },
    {
      path: '/admin',
      name: 'Admin',
      component: () => import('@/views/admin/AdminDashboard.vue'),
      meta: { title: 'Admin Dashboard - SkyFlow', requiresAuth: true, requiresAdmin: true }
    },
    {
      path: '/admin/flights',
      name: 'AdminFlights',
      component: () => import('@/views/admin/AdminFlights.vue'),
      meta: { title: 'Manage Flights - SkyFlow', requiresAuth: true, requiresAdmin: true }
    },
    {
      path: '/admin/users',
      name: 'AdminUsers',
      component: () => import('@/views/admin/AdminUsers.vue'),
      meta: { title: 'Manage Users - SkyFlow', requiresAuth: true, requiresAdmin: true }
    },
    {
      path: '/admin/bookings',
      name: 'AdminBookings',
      component: () => import('@/views/admin/AdminBookings.vue'),
      meta: { title: 'Manage Bookings - SkyFlow', requiresAuth: true, requiresAdmin: true }
    },
    {
      path: '/about',
      name: 'About',
      component: () => import('@/views/About.vue'),
      meta: { title: 'About - SkyFlow' }
    },
    {
      path: '/contact',
      name: 'Contact',
      component: () => import('@/views/Contact.vue'),
      meta: { title: 'Contact - SkyFlow' }
    },
    {
      path: '/404',
      name: 'NotFound',
      component: () => import('@/views/NotFound.vue'),
      meta: { title: 'Page Not Found - SkyFlow' }
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/404'
    }
  ],
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) {
      return savedPosition
    } else {
      return { top: 0 }
    }
  }
})

// Navigation guards
router.beforeEach(async (to, from, next) => {
  const authStore = useAuthStore()
  const userStore = useUserStore()

  // Set page title
  document.title = to.meta.title as string || 'SkyFlow'

  // Check if route requires authentication
  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    next('/login')
    return
  }

  // Check if route requires admin access
  if (to.meta.requiresAdmin && !userStore.isAdmin) {
    next('/dashboard')
    return
  }

  // Redirect authenticated users away from guest routes
  if (to.meta.guest && authStore.isAuthenticated) {
    next('/dashboard')
    return
  }

  // Load user data if authenticated
  if (authStore.isAuthenticated && !userStore.user) {
    try {
      await userStore.fetchUser()
    } catch (error) {
      console.error('Failed to fetch user data:', error)
      authStore.logout()
      next('/login')
      return
    }
  }

  next()
})

export default router
