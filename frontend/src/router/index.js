import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '@/layout/AppLayout.vue'
import LoginView from '@/views/login/LoginView.vue'
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
    component: AppLayout,
    redirect: '/document',
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

  if (!to.meta.public && !token) {
    next('/login')
    return
  }

  if (to.path === '/login' && token) {
    next('/')
    return
  }

  if (to.meta.requiresAdmin && user?.role !== 'SUPER_ADMIN') {
    next('/')
    return
  }

  next()
})

export default router
