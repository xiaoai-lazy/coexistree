import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '@/layout/AppLayout.vue'

const routes = [
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
      }
    ]
  }
]

export default createRouter({
  history: createWebHistory(),
  routes
})
