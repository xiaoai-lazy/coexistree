<template>
  <el-dialog
    v-model="visible"
    title="成员管理"
    width="700px"
    :close-on-click-modal="false"
  >
    <!-- Member List -->
    <el-table :data="members" style="width: 100%" v-loading="loading">
      <el-table-column prop="username" label="用户名" />
      <el-table-column prop="displayName" label="显示名" />
      <el-table-column prop="relationType" label="角色">
        <template #default="{ row }">
          <el-tag v-if="row.relationType === 'OWNER'" type="danger">主人</el-tag>
          <el-tag v-else-if="row.relationType === 'MAINTAINER'" type="warning">维护人</el-tag>
          <el-tag v-else type="info">订阅者</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="viewLevel" label="查看等级">
        <template #default="{ row }">
          <el-select
            v-if="row.relationType !== 'OWNER' && canManage"
            v-model="row.viewLevel"
            size="small"
            style="width: 100px"
            @change="(val) => handleViewLevelChange(row, val)"
          >
            <el-option v-for="n in 5" :key="n" :label="n" :value="n" />
          </el-select>
          <span v-else>{{ row.viewLevel }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button
            v-if="row.relationType !== 'OWNER' && canManage"
            type="danger"
            size="small"
            @click="handleRemove(row)"
          >移除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- Add Member Form -->
    <el-divider v-if="canManage" content-position="left">添加成员</el-divider>

    <el-form
      v-if="canManage"
      :model="form"
      :inline="true"
      class="add-member-form"
    >
      <el-form-item label="用户名" required>
        <el-input v-model="form.username" placeholder="输入用户名" />
      </el-form-item>
      <el-form-item label="角色" required>
        <el-select v-model="form.relationType" placeholder="选择角色">
          <el-option label="维护人" value="MAINTAINER" />
          <el-option label="订阅者" value="SUBSCRIBER" />
        </el-select>
      </el-form-item>
      <el-form-item label="查看等级" required>
        <el-select v-model="form.viewLevel">
          <el-option v-for="n in 5" :key="n" :label="n" :value="n" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleAdd" :loading="adding">添加</el-button>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { confirmDelete } from '@/utils/dialog'
import { listMembers, addMember, removeMember, updateViewLevel } from '@/api/member'
import { useAuthStore } from '@/stores/auth'

const props = defineProps({
  modelValue: Boolean,
  systemId: Number
})

const emit = defineEmits(['update:modelValue', 'refresh'])

const authStore = useAuthStore()
const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const loading = ref(false)
const adding = ref(false)
const members = ref([])

const form = ref({
  username: '',
  relationType: 'SUBSCRIBER',
  viewLevel: 1
})

const canManage = computed(() => {
  return authStore.isAdmin || members.value.some(m =>
    m.userId === authStore.currentUser?.userId &&
    (m.relationType === 'OWNER' || m.relationType === 'MAINTAINER')
  )
})

const fetchMembers = async () => {
  if (!props.systemId) return
  loading.value = true
  try {
    const res = await listMembers(props.systemId)
    if (res.success) {
      members.value = res.data
    }
  } finally {
    loading.value = false
  }
}

const handleAdd = async () => {
  if (!form.value.username) {
    ElMessage.warning('请输入用户名')
    return
  }
  adding.value = true
  try {
    const res = await addMember(props.systemId, form.value)
    if (res.success) {
      ElMessage.success('添加成功')
      form.value.username = ''
      fetchMembers()
      emit('refresh')
    }
  } finally {
    adding.value = false
  }
}

const handleRemove = async (row) => {
  try {
    await confirmDelete(row.displayName || row.username)
    const res = await removeMember(props.systemId, row.userId)
    if (res.success) {
      ElMessage.success('移除成功')
      fetchMembers()
      emit('refresh')
    }
  } catch (e) {
    // Cancelled
  }
}

const handleViewLevelChange = async (row, val) => {
  try {
    const res = await updateViewLevel(props.systemId, row.userId, val)
    if (res.success) {
      ElMessage.success('修改成功')
    }
  } catch (e) {
    fetchMembers() // Refresh on error
  }
}

watch(() => props.modelValue, (val) => {
  if (val) fetchMembers()
})
</script>

<style scoped>
.add-member-form {
  margin-top: 20px;
}
</style>
