<template>
  <div class="member-overview">
    <div class="page-header">
      <div class="header-info">
        <h2 class="page-title">成员概览</h2>
        <p class="page-desc">跨系统成员关系统计与分布</p>
      </div>
    </div>

    <!-- Stats Cards -->
    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-value">{{ stats.totalUsers || 0 }}</div>
        <div class="stat-label">总用户数</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ stats.totalSystems || 0 }}</div>
        <div class="stat-label">系统总数</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ stats.totalMemberships || 0 }}</div>
        <div class="stat-label">成员关系数</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ stats.avgMembersPerSystem?.toFixed(1) || 0 }}</div>
        <div class="stat-label">平均每系统成员</div>
      </div>
    </div>

    <!-- Distribution Table -->
    <div class="table-card">
      <h3 class="section-title">用户系统参与度</h3>
      <el-table
        :data="userParticipation"
        v-loading="loading"
        class="data-table"
        :header-cell-style="{ background: '#f8fafc' }"
      >
        <el-table-column label="用户" min-width="200">
          <template #default="{ row }">
            <div class="user-cell">
              <el-avatar :size="32" :icon="UserFilled" />
              <div class="user-info">
                <div class="username">{{ row.username }}</div>
                <div class="display-name">{{ row.displayName }}</div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="角色" width="140">
          <template #default="{ row }">
            <el-tag :type="row.role === 'SUPER_ADMIN' ? 'danger' : 'info'" size="small">
              {{ row.role === 'SUPER_ADMIN' ? '超级管理员' : '普通用户' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="systemCount" label="参与系统数" width="120" align="center" sortable />

        <el-table-column prop="ownerCount" label="拥有系统" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.ownerCount > 0" type="danger" size="small">{{ row.ownerCount }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>

        <el-table-column prop="maintainerCount" label="维护系统" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.maintainerCount > 0" type="warning" size="small">{{ row.maintainerCount }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="120" align="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="showUserSystems(row)">
              查看详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && userParticipation.length === 0" description="暂无数据" />
    </div>

    <!-- User Systems Detail Dialog -->
    <el-dialog v-model="detailDialogVisible" :title="`${detailTarget?.username} - 系统详情`" width="600px">
      <el-table :data="userSystems" v-loading="detailLoading" size="small">
        <el-table-column prop="systemName" label="系统名称" />
        <el-table-column prop="systemCode" label="系统编码" />
        <el-table-column label="角色">
          <template #default="{ row }">
            <el-tag v-if="row.relationType === 'OWNER'" type="danger" size="small">主人</el-tag>
            <el-tag v-else-if="row.relationType === 'MAINTAINER'" type="warning" size="small">维护人</el-tag>
            <el-tag v-else type="info" size="small">订阅者</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="viewLevel" label="查看等级" width="100" align="center" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { UserFilled } from '@element-plus/icons-vue'
import { listUsers } from '@/api/admin'
import { listSystems } from '@/api/system'
import { listMembers } from '@/api/member'

const loading = ref(false)
const stats = ref({})
const userParticipation = ref([])

// Load data
const loadData = async () => {
  loading.value = true
  try {
    const [usersRes, systemsRes] = await Promise.all([
      listUsers().catch(() => ({ success: false, data: [] })),
      listSystems().catch(() => ({ success: false, data: [] })),
    ])

    const users = usersRes.success ? usersRes.data || [] : []
    const systems = systemsRes.success ? systemsRes.data || [] : []

    // Calculate membership data for each user
    const participationData = await Promise.all(
      users.map(async (user) => {
        // Find systems where user is a member
        let systemCount = 0
        let ownerCount = 0
        let maintainerCount = 0

        for (const system of systems) {
          try {
            const membersRes = await listMembers(system.id)
            if (membersRes.success) {
              const members = membersRes.data || []
              const membership = members.find((m) => m.userId === user.id)
              if (membership) {
                systemCount++
                if (membership.relationType === 'OWNER') ownerCount++
                else if (membership.relationType === 'MAINTAINER') maintainerCount++
              }
            }
          } catch {
            // Ignore errors for individual systems
          }
        }

        return {
          ...user,
          systemCount,
          ownerCount,
          maintainerCount,
        }
      })
    )

    // Sort by system count desc
    userParticipation.value = participationData.sort((a, b) => b.systemCount - a.systemCount)

    // Calculate stats
    const totalMemberships = userParticipation.value.reduce((sum, u) => sum + u.systemCount, 0)
    stats.value = {
      totalUsers: users.length,
      totalSystems: systems.length,
      totalMemberships,
      avgMembersPerSystem: systems.length > 0 ? totalMemberships / systems.length : 0,
    }
  } catch {
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

// Show user systems detail
const detailDialogVisible = ref(false)
const detailLoading = ref(false)
const detailTarget = ref(null)
const userSystems = ref([])

const showUserSystems = async (row) => {
  detailTarget.value = row
  detailDialogVisible.value = true
  detailLoading.value = true

  try {
    const systemsRes = await listSystems().catch(() => ({ success: false, data: [] }))
    const systems = systemsRes.success ? systemsRes.data || [] : []

    const memberships = []
    for (const system of systems) {
      try {
        const membersRes = await listMembers(system.id)
        if (membersRes.success) {
          const members = membersRes.data || []
          const membership = members.find((m) => m.userId === row.id)
          if (membership) {
            memberships.push({
              systemName: system.systemName,
              systemCode: system.systemCode,
              relationType: membership.relationType,
              viewLevel: membership.viewLevel,
            })
          }
        }
      } catch {
        // Ignore errors
      }
    }

    userSystems.value = memberships
  } catch {
    ElMessage.error('加载详情失败')
  } finally {
    detailLoading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.member-overview {
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 24px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0 0 8px 0;
}

.page-desc {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin: 0;
}

/* Stats */
.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  background: var(--color-bg-card);
  border-radius: var(--radius-lg);
  padding: 20px;
  text-align: center;
  box-shadow: var(--shadow-sm);
}

.stat-value {
  font-size: 32px;
  font-weight: 600;
  color: var(--color-primary);
  line-height: 1;
  margin-bottom: 8px;
}

.stat-label {
  font-size: 13px;
  color: var(--color-text-secondary);
}

/* Table */
.table-card {
  background: var(--color-bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
  padding: 20px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0 0 16px 0;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.username {
  font-weight: 500;
  color: var(--color-text-primary);
}

.display-name {
  font-size: 12px;
  color: var(--color-text-tertiary);
}

@media (max-width: 768px) {
  .stats-row {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
