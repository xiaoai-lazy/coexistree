<template>
  <div class="system-management">
    <div class="page-header">
      <div class="header-info">
        <h2 class="page-title">系统管理</h2>
        <p class="page-desc">查看和管理所有系统，支持转移系统所有权</p>
      </div>
    </div>

    <!-- System Table -->
    <div class="table-card">
      <el-table
        :data="systems"
        v-loading="loading"
        class="data-table"
        :header-cell-style="{ background: '#f8fafc' }"
      >
        <el-table-column label="系统名称" min-width="200">
          <template #default="{ row }">
            <div class="system-cell">
              <div class="system-avatar">{{ row.systemName.charAt(0) }}</div>
              <div class="system-info">
                <div class="system-name">{{ row.systemName }}</div>
                <div class="system-code">{{ row.systemCode }}</div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="所有者" width="150">
          <template #default="{ row }">
            {{ row.ownerUsername || '-' }}
          </template>
        </el-table-column>

        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
              {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="createdAt" label="创建时间" width="160">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>

        <el-table-column label="成员数" width="100" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="showMembers(row)">
              {{ row.memberCount || 0 }}
            </el-button>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="150" align="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openTransferDialog(row)">
              转移所有权
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && systems.length === 0" description="暂无系统数据" />
    </div>

    <!-- Transfer Ownership Dialog -->
    <el-dialog
      v-model="transferDialogVisible"
      title="转移系统所有权"
      width="480px"
      :close-on-click-modal="false"
    >
      <p class="dialog-tip">
        正在转移系统 <strong>{{ transferTarget?.systemName }}</strong> 的所有权
      </p>
      <el-form :model="transferForm" :rules="transferRules" ref="transferFormRef" label-width="100px">
        <el-form-item label="新所有者" prop="newOwnerId">
          <el-select
            v-model="transferForm.newOwnerId"
            placeholder="选择新所有者"
            style="width: 100%"
            filterable
          >
            <el-option
              v-for="user in eligibleOwners"
              :key="user.id"
              :label="`${user.username} (${user.displayName})`"
              :value="user.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="transferLoading" @click="handleTransfer">
          确认转移
        </el-button>
      </template>
    </el-dialog>

    <!-- Member List Dialog -->
    <el-dialog v-model="membersDialogVisible" title="系统成员" width="600px">
      <el-table :data="currentMembers" v-loading="membersLoading" size="small">
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="displayName" label="显示名" />
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
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { confirm } from '@/utils/dialog'
import { listAllSystems, transferSystemOwnership } from '@/api/admin'
import { listMembers } from '@/api/member'
import { listUsers } from '@/api/admin'

const loading = ref(false)
const systems = ref([])
const users = ref([])

// Load systems and users
const loadData = async () => {
  loading.value = true
  try {
    const [systemsRes, usersRes] = await Promise.all([
      listAllSystems().catch(() => ({ success: false, data: [] })),
      listUsers().catch(() => ({ success: false, data: [] })),
    ])
    if (systemsRes.success) {
      systems.value = systemsRes.data || []
    }
    if (usersRes.success) {
      users.value = usersRes.data || []
    }
  } finally {
    loading.value = false
  }
}

// Get eligible owners (all enabled users)
const eligibleOwners = computed(() => {
  return users.value.filter((u) => u.enabled !== false)
})

// Format date
const formatDate = (dateStr) => {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

// Transfer ownership
const transferDialogVisible = ref(false)
const transferLoading = ref(false)
const transferFormRef = ref()
const transferTarget = ref(null)
const transferForm = ref({
  newOwnerId: null,
})

const transferRules = {
  newOwnerId: [{ required: true, message: '请选择新所有者', trigger: 'change' }],
}

const openTransferDialog = (row) => {
  transferTarget.value = row
  transferForm.value = { newOwnerId: null }
  transferDialogVisible.value = true
}

const handleTransfer = async () => {
  const valid = await transferFormRef.value?.validate().catch(() => false)
  if (!valid) return

  try {
    await confirm(
      `确定将系统 "${transferTarget.value.systemName}" 的所有权转移给选定的用户吗？`,
      '确认转移',
      'warning'
    )

    transferLoading.value = true
    const res = await transferSystemOwnership(
      transferTarget.value.id,
      transferForm.value.newOwnerId
    )
    if (res.success) {
      ElMessage.success('所有权转移成功')
      transferDialogVisible.value = false
      loadData()
    }
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error(err.response?.data?.message || '转移失败')
    }
  } finally {
    transferLoading.value = false
  }
}

// Show members
const membersDialogVisible = ref(false)
const membersLoading = ref(false)
const currentMembers = ref([])

const showMembers = async (row) => {
  membersDialogVisible.value = true
  membersLoading.value = true
  try {
    const res = await listMembers(row.id)
    if (res.success) {
      currentMembers.value = res.data || []
    }
  } catch {
    ElMessage.error('加载成员列表失败')
  } finally {
    membersLoading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.system-management {
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

.table-card {
  background: var(--color-bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
  padding: 20px;
}

.system-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.system-avatar {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: linear-gradient(135deg, #2563eb, #3b82f6);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  flex-shrink: 0;
}

.system-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.system-name {
  font-weight: 500;
  color: var(--color-text-primary);
}

.system-code {
  font-size: 12px;
  color: var(--color-text-tertiary);
  font-family: monospace;
}

.dialog-tip {
  margin: 0 0 16px 0;
  color: var(--color-text-secondary);
}
</style>
