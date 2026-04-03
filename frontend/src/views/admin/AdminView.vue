<template>
  <div class="admin-page">
    <!-- Admin Sidebar -->
    <aside class="admin-sidebar">
      <div class="sidebar-header">
        <h3>系统管理</h3>
      </div>

      <nav class="admin-nav">
        <div
          v-for="item in navItems"
          :key="item.key"
          class="nav-item"
          :class="{ active: activeTab === item.key }"
          @click="activeTab = item.key"
        >
          <el-icon :size="18"><component :is="item.icon" /></el-icon>
          <span class="nav-label">{{ item.label }}</span>
        </div>
      </nav>
    </aside>

    <!-- Main Content -->
    <main class="admin-main">
      <!-- 用户管理 -->
      <UserManagement v-if="activeTab === 'users'" />

      <!-- 系统管理 -->
      <SystemManagement v-if="activeTab === 'systems'" />

      <!-- 成员概览 -->
      <MemberOverview v-if="activeTab === 'members'" />
    </main>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { UserFilled, OfficeBuilding, User } from '@element-plus/icons-vue'
import UserManagement from './components/UserManagement.vue'
import SystemManagement from './components/SystemManagement.vue'
import MemberOverview from './components/MemberOverview.vue'

const activeTab = ref('users')

const navItems = [
  { key: 'users', label: '用户管理', icon: UserFilled },
  { key: 'systems', label: '系统管理', icon: OfficeBuilding },
  { key: 'members', label: '成员概览', icon: User },
]
</script>

<style scoped>
.admin-page {
  height: 100%;
  display: flex;
  overflow: hidden;
  background: var(--color-bg-page);
}

/* Sidebar */
.admin-sidebar {
  width: 220px;
  min-width: 220px;
  background: var(--color-bg-card);
  border-right: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 20px;
  border-bottom: 1px solid var(--color-border-light);
}

.sidebar-header h3 {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0;
}

.admin-nav {
  flex: 1;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all 0.2s;
  color: var(--color-text-secondary);
  font-size: 14px;
}

.nav-item:hover {
  background: var(--color-bg-hover);
  color: var(--color-text-primary);
}

.nav-item.active {
  background: var(--color-sidebar-active);
  color: var(--color-primary);
  font-weight: 500;
}

/* Main Content */
.admin-main {
  flex: 1;
  overflow: auto;
  padding: 24px;
}
</style>
