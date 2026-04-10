<template>
  <el-container class="app-layout">
    <!-- Sidebar -->
    <el-aside class="app-aside" :width="sidebarWidth + 'px'">
      <div class="sidebar-header">
        <div class="logo">
          <div class="logo-icon">
            <el-icon :size="24"><Collection /></el-icon>
          </div>
          <span class="logo-text">CoExistree</span>
        </div>
      </div>

      <nav class="sidebar-nav">
        <router-link
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: currentPath === item.path }"
        >
          <el-icon :size="18"><component :is="item.icon" /></el-icon>
          <span class="nav-label">{{ item.label }}</span>
        </router-link>
      </nav>

      <div class="sidebar-footer">
        <div class="user-info">
          <div class="user-avatar">
            {{ userInitials }}
          </div>
          <div class="user-details">
            <span class="user-name">{{ displayName }}</span>
          </div>
        </div>
        <button class="logout-btn" @click="handleLogout" title="退出登录">
          <el-icon :size="16"><SwitchButton /></el-icon>
          <span>退出登录</span>
        </button>
        <div class="version">v1.0.0</div>
      </div>
    </el-aside>

    <!-- Main Content -->
    <el-main class="app-main">
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Document, ChatDotRound, Setting, Collection, SwitchButton } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const currentPath = computed(() => route.path)
const sidebarWidth = 220

const authStore = useAuthStore()

const displayName = computed(() => {
  const user = authStore.currentUser
  return user?.username || user?.email || '用户'
})

const userInitials = computed(() => {
  const name = displayName.value
  return name.charAt(0).toUpperCase()
})

const handleLogout = async () => {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning'
    })
    authStore.clearAuth()
    router.push('/login')
  } catch {
    // User cancelled
  }
}

const navItems = computed(() => {
  const items = [
    { path: '/app/document', icon: Document, label: '知识库' },
    { path: '/app/chat', icon: ChatDotRound, label: '问答' },
  ]
  if (authStore.isAdmin) {
    items.push({ path: '/app/admin', icon: Setting, label: '系统管理' })
  }
  return items
})
</script>

<style scoped>
.app-layout {
  height: 100vh;
  width: 100vw;
  overflow: hidden;
  display: flex;
}

.app-aside {
  height: 100vh;
  background: var(--color-sidebar-bg);
  border-right: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  transition: width 0.3s ease;
}

.sidebar-header {
  padding: 20px 24px;
  border-bottom: 1px solid var(--color-border-light);
}

.logo {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-icon {
  width: 36px;
  height: 36px;
  background: linear-gradient(135deg, #2563eb, #3b82f6);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  flex-shrink: 0;
}

.logo-text {
  font-size: 18px;
  font-weight: 700;
  color: var(--color-text-primary);
  letter-spacing: -0.5px;
}

.sidebar-nav {
  flex: 1;
  padding: 16px 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  overflow-y: auto;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-radius: var(--radius-md);
  color: var(--color-text-secondary);
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.2s ease;
  cursor: pointer;
}

.nav-item:hover {
  background: var(--color-bg-hover);
  color: var(--color-text-primary);
}

.nav-item.active {
  background: var(--color-sidebar-active);
  color: var(--color-primary);
  font-weight: 600;
}

.nav-item.active::before {
  content: '';
  position: absolute;
  left: 0;
  width: 3px;
  height: 20px;
  background: var(--color-primary);
  border-radius: 0 2px 2px 0;
}

.nav-label {
  position: relative;
}

.sidebar-footer {
  padding: 16px 16px 12px;
  border-top: 1px solid var(--color-border-light);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 0;
}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-md);
  background: linear-gradient(135deg, #2563eb, #3b82f6);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  flex-shrink: 0;
}

.user-details {
  display: flex;
  flex-direction: column;
  gap: 0;
  min-width: 0;
}

.user-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.logout-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
  width: 100%;
  justify-content: center;
}

.logout-btn:hover {
  background: #fef2f2;
  border-color: #fecaca;
  color: #dc2626;
}

.logout-btn:active {
  transform: scale(0.98);
}

.logout-btn:focus-visible {
  outline: 2px solid #ef4444;
  outline-offset: 1px;
}

.version {
  font-size: 12px;
  color: var(--color-text-tertiary);
  text-align: center;
}

.app-main {
  flex: 1;
  height: 100vh;
  overflow: hidden;
  background: var(--color-bg-page);
  padding: 0;
}

.app-main > :deep(*) {
  height: 100%;
  overflow: hidden;
}
</style>
