import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '@/layout/AppLayout.vue'
import LoginView from '@/views/login/LoginView.vue'
import LandingView from '@/views/landing/LandingView.vue'
import { useAuthStore } from '@/stores/auth'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: LoginView,
    meta: { public: true }
  },
  {
    path: '/',
    name: 'Landing',
    component: LandingView,
    meta: { public: true }
  },
  {
    path: '/app',
    component: AppLayout,
    redirect: '/app/chat',
    children: [
      {
        path: 'document',
        component: () => import('@/views/document/DocumentView.vue')
      },
      {
        path: 'chat',
        component: () => import('@/views/chat/ChatView.vue')
      },
      {
        path: 'admin',
        component: () => import('@/views/admin/AdminView.vue'),
        meta: { requiresAdmin: true }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// Navigation guard
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  const userStr = localStorage.getItem('user')
  const user = userStr ? JSON.parse(userStr) : null

  // Unauthenticated users trying to access protected routes -> login
  if (!to.meta.public && !token) {
    next('/login')
    return
  }

  // Authenticated users trying to access login -> go to app
  if (to.path === '/login' && token) {
    next('/app/chat')
    return
  }

  // Unauthenticated users on home -> stay on landing (it's public)
  // Authenticated users on home -> redirect to app
  if (to.path === '/' && token) {
    next('/app/chat')
    return
  }

  // Admin check
  if (to.meta.requiresAdmin && user?.role !== 'SUPER_ADMIN') {
    next('/app/chat')
    return
  }

  next()
})

export default router
