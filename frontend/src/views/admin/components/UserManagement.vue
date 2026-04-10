<template>
  <div class="user-management">
    <div class="page-header">
      <div class="header-info">
        <h2 class="page-title">用户管理</h2>
        <p class="page-desc">管理系统用户，分配角色和权限</p>
      </div>
      <el-button type="primary" @click="openCreateDialog">
        <el-icon><Plus /></el-icon>
        <span>新建用户</span>
      </el-button>
    </div>

    <!-- User Table -->
    <div class="table-card">
      <el-table
        :data="users"
        v-loading="loading"
        class="data-table"
        :header-cell-style="{ background: '#f8fafc' }"
      >
        <el-table-column label="用户" min-width="200">
          <template #default="{ row }">
            <div class="user-cell">
              <el-avatar :size="36" :icon="UserFilled" />
              <div class="user-info">
                <div class="username">{{ row.username }}</div>
                <div class="display-name">{{ row.displayName }}</div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="角色" width="140">
          <template #default="{ row }">
            <el-tag
              :type="row.role === 'SUPER_ADMIN' ? 'danger' : 'info'"
              size="small"
            >
              {{ row.role === 'SUPER_ADMIN' ? '超级管理员' : '普通用户' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.enabled"
              :disabled="row.role === 'SUPER_ADMIN'"
              @change="(val) => handleStatusChange(row, val)"
            />
          </template>
        </el-table-column>

        <el-table-column prop="createdAt" label="创建时间" width="160">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>

        <el-table-column prop="lastLoginAt" label="最后登录" width="160">
          <template #default="{ row }">
            {{ row.lastLoginAt ? formatDate(row.lastLoginAt) : '-' }}
          </template>
        </el-table-column>

        <el-table-column label="操作" width="200" align="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openEditDialog(row)">
              编辑
            </el-button>
            <el-button type="primary" link size="small" @click="openResetPasswordDialog(row)">
              重置密码
            </el-button>
            <el-button
              v-if="row.role !== 'SUPER_ADMIN'"
              type="danger"
              link
              size="small"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && users.length === 0" description="暂无用户数据" />
    </div>

    <!-- Create User Dialog -->
    <el-dialog
      v-model="createDialogVisible"
      title="新建用户"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-form :model="createForm" :rules="createRules" ref="createFormRef" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="createForm.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="createForm.password"
            type="password"
            placeholder="至少8位，包含大小写字母和数字"
            show-password
          />
        </el-form-item>
        <el-form-item label="显示名" prop="displayName">
          <el-input v-model="createForm.displayName" placeholder="请输入显示名称" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="createLoading" @click="handleCreate">确定</el-button>
      </template>
    </el-dialog>

    <!-- Edit User Dialog -->
    <el-dialog
      v-model="editDialogVisible"
      title="编辑用户"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-form :model="editForm" :rules="editRules" ref="editFormRef" label-width="80px">
        <el-form-item label="用户名">
          <el-input v-model="editForm.username" disabled />
        </el-form-item>
        <el-form-item label="显示名" prop="displayName">
          <el-input v-model="editForm.displayName" placeholder="请输入显示名称" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="editForm.role" placeholder="选择角色" style="width: 100%">
            <el-option label="普通用户" value="USER" />
            <el-option
              v-if="editForm.role === 'SUPER_ADMIN'"
              label="超级管理员"
              value="SUPER_ADMIN"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="enabled">
          <el-switch
            v-model="editForm.enabled"
            :disabled="editForm.role === 'SUPER_ADMIN'"
            active-text="启用"
            inactive-text="禁用"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="editLoading" @click="handleEdit">确定</el-button>
      </template>
    </el-dialog>

    <!-- Reset Password Dialog -->
    <el-dialog
      v-model="resetPasswordVisible"
      title="重置密码"
      width="400px"
      :close-on-click-modal="false"
    >
      <p class="dialog-tip">正在为 <strong>{{ resetPasswordTarget?.username }}</strong> 重置密码</p>
      <el-form :model="resetPasswordForm" :rules="resetPasswordRules" ref="resetPasswordFormRef">
        <el-form-item label="新密码" prop="newPassword" label-width="80px">
          <el-input
            v-model="resetPasswordForm.newPassword"
            type="password"
            placeholder="至少8位，包含大小写字母和数字"
            show-password
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetPasswordVisible = false">取消</el-button>
        <el-button type="primary" :loading="resetPasswordLoading" @click="handleResetPassword">
          确认重置
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Plus, UserFilled } from '@element-plus/icons-vue'
import { confirmDelete } from '@/utils/dialog'
import { listUsers, createUser, updateUser, deleteUser, resetPassword } from '@/api/admin'

const loading = ref(false)
const users = ref([])

// Load users
const loadUsers = async () => {
  loading.value = true
  try {
    const res = await listUsers()
    if (res.success) {
      users.value = res.data || []
    }
  } catch (err) {
    ElMessage.error('加载用户列表失败')
  } finally {
    loading.value = false
  }
}

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

// Create user
const createDialogVisible = ref(false)
const createLoading = ref(false)
const createFormRef = ref()
const createForm = ref({
  username: '',
  password: '',
  displayName: '',
})

const createRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 64, message: '长度在 3 到 64 个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    {
      pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$/,
      message: '密码必须至少8位，包含大小写字母和数字',
      trigger: 'blur',
    },
  ],
  displayName: [
    { required: true, message: '请输入显示名称', trigger: 'blur' },
    { max: 128, message: '长度不超过128个字符', trigger: 'blur' },
  ],
}

const openCreateDialog = () => {
  createForm.value = { username: '', password: '', displayName: '' }
  createDialogVisible.value = true
}

const handleCreate = async () => {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return

  createLoading.value = true
  try {
    const res = await createUser(createForm.value)
    if (res.success) {
      ElMessage.success('创建成功')
      createDialogVisible.value = false
      loadUsers()
    }
  } catch (err) {
    ElMessage.error(err.response?.data?.message || '创建失败')
  } finally {
    createLoading.value = false
  }
}

// Edit user
const editDialogVisible = ref(false)
const editLoading = ref(false)
const editFormRef = ref()
const editForm = ref({
  id: null,
  username: '',
  displayName: '',
  role: 'USER',
  enabled: true,
})

const editRules = {
  displayName: [
    { required: true, message: '请输入显示名称', trigger: 'blur' },
    { max: 128, message: '长度不超过128个字符', trigger: 'blur' },
  ],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
}

const openEditDialog = (row) => {
  editForm.value = {
    id: row.id,
    username: row.username,
    displayName: row.displayName,
    role: row.role,
    enabled: row.enabled,
  }
  editDialogVisible.value = true
}

const handleEdit = async () => {
  const valid = await editFormRef.value?.validate().catch(() => false)
  if (!valid) return

  editLoading.value = true
  try {
    const res = await updateUser(editForm.value.id, {
      displayName: editForm.value.displayName,
      role: editForm.value.role,
      enabled: editForm.value.enabled,
    })
    if (res.success) {
      ElMessage.success('更新成功')
      editDialogVisible.value = false
      loadUsers()
    }
  } catch (err) {
    ElMessage.error(err.response?.data?.message || '更新失败')
  } finally {
    editLoading.value = false
  }
}

// Status change
const handleStatusChange = async (row, val) => {
  try {
    const res = await updateUser(row.id, {
      displayName: row.displayName,
      role: row.role,
      enabled: val,
    })
    if (res.success) {
      ElMessage.success(val ? '已启用' : '已禁用')
    }
  } catch (err) {
    row.enabled = !val
    ElMessage.error(err.response?.data?.message || '操作失败')
  }
}

// Delete user
const handleDelete = async (row) => {
  try {
    await confirmDelete(row.username, '此操作不可恢复')
    const res = await deleteUser(row.id)
    if (res.success) {
      ElMessage.success('删除成功')
      loadUsers()
    }
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error(err.response?.data?.message || '删除失败')
    }
  }
}

// Reset password
const resetPasswordVisible = ref(false)
const resetPasswordLoading = ref(false)
const resetPasswordFormRef = ref()
const resetPasswordTarget = ref(null)
const resetPasswordForm = ref({ newPassword: '' })

const resetPasswordRules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    {
      pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$/,
      message: '密码必须至少8位，包含大小写字母和数字',
      trigger: 'blur',
    },
  ],
}

const openResetPasswordDialog = (row) => {
  resetPasswordTarget.value = row
  resetPasswordForm.value = { newPassword: '' }
  resetPasswordVisible.value = true
}

const handleResetPassword = async () => {
  const valid = await resetPasswordFormRef.value?.validate().catch(() => false)
  if (!valid) return

  resetPasswordLoading.value = true
  try {
    const res = await resetPassword(resetPasswordTarget.value.id, resetPasswordForm.value.newPassword)
    if (res.success) {
      ElMessage.success('密码重置成功')
      resetPasswordVisible.value = false
    }
  } catch (err) {
    ElMessage.error(err.response?.data?.message || '重置失败')
  } finally {
    resetPasswordLoading.value = false
  }
}

onMounted(loadUsers)
</script>

<style scoped>
.user-management {
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

.data-table {
  width: 100%;
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

.dialog-tip {
  margin: 0 0 16px 0;
  color: var(--color-text-secondary);
}
</style>
