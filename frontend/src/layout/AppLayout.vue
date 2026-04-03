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
import { useRoute } from 'vue-router'
import { Document, ChatDotRound, Setting, Collection } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const currentPath = computed(() => route.path)
const sidebarWidth = 220

const authStore = useAuthStore()

const navItems = computed(() => {
  const items = [
    { path: '/document', icon: Document, label: '知识库' },
    { path: '/chat', icon: ChatDotRound, label: '问答' },
  ]
  if (authStore.isAdmin) {
    items.push({ path: '/admin', icon: Setting, label: '系统管理' })
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
  padding: 16px 24px;
  border-top: 1px solid var(--color-border-light);
}

.version {
  font-size: 12px;
  color: var(--color-text-tertiary);
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
