<template>
  <div class="page-container">
    <!-- System Sidebar -->
    <aside class="system-sidebar">
      <div class="sidebar-header">
        <h3>系统列表</h3>
        <el-button type="primary" link size="small" @click="openCreateSystemDialog">
          <el-icon><Plus /></el-icon>
        </el-button>
      </div>

      <div class="system-list">
        <div
          v-for="sys in systems"
          :key="sys.id"
          class="system-item"
          :class="{ active: selectedSystemId === sys.id }"
          @click="selectSystem(sys.id)"
        >
          <div class="system-avatar">{{ sys.systemName.charAt(0) }}</div>
          <div class="system-info">
            <span class="system-name">{{ sys.systemName }}</span>
            <span class="system-code">{{ sys.systemCode }}</span>
          </div>
          <el-dropdown trigger="click" @command="handleSystemCommand($event, sys)">
            <el-button link size="small" class="more-btn" @click.stop>
              <el-icon><MoreFilled /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="edit">编辑</el-dropdown-item>
                <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>

        <el-empty v-if="systems.length === 0" description="暂无系统" :image-size="60" />
      </div>
    </aside>

    <!-- Main Content -->
    <main class="main-content">
      <template v-if="selectedSystem">
        <!-- Header -->
        <div class="content-header">
          <div class="header-info">
            <div class="system-title">
              <span class="title">{{ selectedSystem.systemName }}</span>
              <el-tag size="small" :type="selectedSystem.status === 'ACTIVE' ? 'success' : 'info'">
                {{ selectedSystem.status === 'ACTIVE' ? '启用' : '停用' }}
              </el-tag>
            </div>
            <p class="system-desc">{{ selectedSystem.description || '暂无描述' }}</p>
          </div>
          <el-button type="primary" @click="uploadModalVisible = true">
            <el-icon><Upload /></el-icon>
            <span>上传文档</span>
          </el-button>
        </div>

        <!-- Document List -->
        <div class="content-body">
          <el-table
            :data="documents"
            v-loading="loading"
            class="data-table"
            :header-cell-style="{ background: '#f8fafc' }"
          >
            <el-table-column label="文件名" min-width="280">
              <template #default="{ row }">
                <div class="file-name-cell">
                  <div class="file-icon-wrapper">
                    <el-icon :size="20" color="#2563eb"><Document /></el-icon>
                  </div>
                  <span class="file-name">{{ row.originalFileName }}</span>
                </div>
              </template>
            </el-table-column>

            <el-table-column prop="uploadTime" label="上传时间" width="140" />

            <el-table-column label="状态" width="120" align="center">
              <template #default="{ row }">
                <span class="status-tag" :class="statusClass(row.parseStatus)">
                  <el-icon v-if="row.parseStatus === 'PROCESSING'" class="is-loading"><Loading /></el-icon>
                  {{ statusLabel(row.parseStatus) }}
                </span>
              </template>
            </el-table-column>

            <el-table-column label="操作" width="100" align="right">
              <template #default="{ row }">
                <el-button link type="danger" size="small" @click="removeDocument(row)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-empty v-if="!loading && documents.length === 0" description="该系统暂无文档">
            <el-button type="primary" size="small" @click="uploadModalVisible = true">
              上传第一个文档
            </el-button>
          </el-empty>
        </div>
      </template>

      <!-- Empty State when no system selected -->
      <div v-else class="empty-system">
        <el-empty description="请选择一个系统或创建新系统">
          <el-button type="primary" @click="openCreateSystemDialog">创建系统</el-button>
        </el-empty>
      </div>
    </main>

    <!-- Create System Dialog -->
    <el-dialog v-model="showCreateDialog" title="新建系统" width="480px" :close-on-click-modal="false">
      <el-form :model="createForm" label-width="80px" class="dialog-form">
        <el-form-item label="系统编码" required>
          <el-input v-model="createForm.systemCode" placeholder="如: order-service" />
        </el-form-item>
        <el-form-item label="系统名称" required>
          <el-input v-model="createForm.systemName" placeholder="请输入系统名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="createForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入系统描述（可选）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" :loading="createLoading" @click="handleCreate">确定</el-button>
      </template>
    </el-dialog>

    <!-- Edit System Dialog -->
    <el-dialog v-model="showEditDialog" title="编辑系统" width="480px" :close-on-click-modal="false">
      <el-form :model="editForm" label-width="80px" class="dialog-form">
        <el-form-item label="系统名称">
          <el-input v-model="editForm.systemName" placeholder="请输入系统名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="editForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入描述"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="editForm.status" placeholder="请选择状态" style="width: 100%">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="INACTIVE" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditDialog = false">取消</el-button>
        <el-button type="primary" :loading="editLoading" @click="handleEdit">确定</el-button>
      </template>
    </el-dialog>

    <!-- Delete System Dialog -->
    <el-dialog v-model="showDeleteDialog" title="确认删除" width="400px">
      <p>确定要删除系统「{{ deleteTarget?.systemName }}」吗？</p>
      <p class="warning-text">删除系统将同时删除该系统下的所有文档，此操作不可恢复。</p>
      <p v-if="deleteError" class="error-message">{{ deleteError }}</p>
      <template #footer>
        <el-button @click="showDeleteDialog = false">取消</el-button>
        <el-button type="danger" :loading="deleteLoading" @click="handleDelete">删除</el-button>
      </template>
    </el-dialog>

    <!-- Upload Document Dialog -->
    <el-dialog
      v-model="uploadModalVisible"
      title="上传文档"
      width="520px"
      :close-on-click-modal="false"
      @close="resetUploadForm"
    >
      <div class="upload-form">
        <div class="form-item">
          <label class="form-label">目标系统</label>
          <el-input :model-value="selectedSystem?.systemName" disabled />
        </div>

        <div class="form-item">
          <label class="form-label required">上传文件</label>
          <div
            class="upload-zone"
            :class="{ 'drag-over': isDragOver, 'has-file': uploadForm.file }"
            @dragover.prevent="isDragOver = true"
            @dragleave="isDragOver = false"
            @drop.prevent="onDrop"
            @click="triggerFileInput"
          >
            <input
              ref="fileInputRef"
              type="file"
              accept=".md,.markdown"
              style="display:none"
              @change="onFileChange"
            />
            <template v-if="!uploadForm.file">
              <div class="upload-icon">
                <el-icon :size="40" color="#94a3b8"><Upload /></el-icon>
              </div>
              <p class="upload-text">点击或拖拽文件到此处</p>
              <p class="upload-hint">支持 .md、.markdown 格式</p>
            </template>
            <template v-else>
              <div class="upload-icon selected">
                <el-icon :size="40" color="#2563eb"><Document /></el-icon>
              </div>
              <p class="upload-text selected">{{ uploadForm.file.name }}</p>
              <p class="upload-hint">点击重新选择</p>
            </template>
          </div>
          <span v-if="fileError" class="form-error">{{ fileError }}</span>
        </div>
      </div>

      <template #footer>
        <el-button @click="uploadModalVisible = false; resetUploadForm()">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="confirmUpload">确认上传</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import http from '@/api/http'
import { listSystems, createSystem, updateSystem, deleteSystem } from '@/api/system'
import { ElMessage, ElMessageBox } from 'element-plus'

// Systems data
const systems = ref([])
const selectedSystemId = ref(null)
const loadingSystems = ref(false)

// Documents data
const documents = ref([])
const loading = ref(false)

let pollTimer = null

const selectedSystem = computed(() =>
  systems.value.find(s => s.id === selectedSystemId.value)
)

const hasProcessing = computed(() =>
  documents.value.some(d => d.parseStatus === 'PENDING' || d.parseStatus === 'PROCESSING')
)

// Load systems
const loadSystems = async () => {
  loadingSystems.value = true
  try {
    const res = await listSystems()
    systems.value = res.data || []
    // Auto-select first system if none selected
    if (systems.value.length > 0 && !selectedSystemId.value) {
      selectedSystemId.value = systems.value[0].id
      await loadDocuments()
    }
  } catch (err) {
    console.error('加载系统列表失败', err)
  } finally {
    loadingSystems.value = false
  }
}

// Select system
const selectSystem = async (id) => {
  selectedSystemId.value = id
  await loadDocuments()
}

// Load documents
const loadDocuments = async () => {
  if (!selectedSystemId.value) return
  loading.value = true
  try {
    const res = await http.get('/v1/documents', {
      params: { systemId: selectedSystemId.value }
    })
    documents.value = (res.data || []).map(d => ({
      ...d,
      uploadTime: d.createdAt ? new Date(d.createdAt).toLocaleDateString('zh-CN') : ''
    }))
    if (hasProcessing.value) startPolling()
  } catch (err) {
    console.error('加载文档列表失败', err)
  } finally {
    loading.value = false
  }
}

// Polling for document status
const startPolling = () => {
  if (pollTimer) return
  pollTimer = setInterval(pollStatuses, 3000)
}

const stopPolling = () => {
  clearInterval(pollTimer)
  pollTimer = null
}

const pollStatuses = async () => {
  const pending = documents.value.filter(
    d => d.parseStatus === 'PENDING' || d.parseStatus === 'PROCESSING'
  )
  await Promise.all(pending.map(refreshStatus))
  if (!hasProcessing.value) stopPolling()
}

const refreshStatus = async (doc) => {
  try {
    const res = await http.get(`/v1/documents/${doc.id}`)
    const updated = res.data
    const idx = documents.value.findIndex(d => d.id === doc.id)
    if (idx !== -1) {
      documents.value[idx] = { ...documents.value[idx], parseStatus: updated.parseStatus, parseError: updated.parseError }
    }
  } catch { }
}

// System CRUD
const showCreateDialog = ref(false)
const createLoading = ref(false)
const createForm = ref({ systemCode: '', systemName: '', description: '' })

const openCreateSystemDialog = () => {
  createForm.value = { systemCode: '', systemName: '', description: '' }
  showCreateDialog.value = true
}

const handleCreate = async () => {
  if (!createForm.value.systemCode || !createForm.value.systemName) {
    ElMessage.warning('请填写系统编码和系统名称')
    return
  }
  createLoading.value = true
  try {
    const res = await createSystem(createForm.value)
    showCreateDialog.value = false
    await loadSystems()
    // Select the newly created system
    if (res.data?.id) {
      selectedSystemId.value = res.data.id
      await loadDocuments()
    }
    ElMessage.success('创建成功')
  } catch (err) {
    ElMessage.error(err.response?.data?.message || '创建失败')
  } finally {
    createLoading.value = false
  }
}

const showEditDialog = ref(false)
const editLoading = ref(false)
const editForm = ref({ id: null, systemName: '', description: '', status: 'ACTIVE' })

const openEditDialog = (row) => {
  editForm.value = {
    id: row.id,
    systemName: row.systemName,
    description: row.description,
    status: row.status
  }
  showEditDialog.value = true
}

const handleEdit = async () => {
  editLoading.value = true
  try {
    await updateSystem(editForm.value.id, {
      systemName: editForm.value.systemName,
      description: editForm.value.description,
      status: editForm.value.status
    })
    showEditDialog.value = false
    await loadSystems()
    ElMessage.success('更新成功')
  } catch (err) {
    ElMessage.error(err.response?.data?.message || '更新失败')
  } finally {
    editLoading.value = false
  }
}

const showDeleteDialog = ref(false)
const deleteLoading = ref(false)
const deleteTarget = ref(null)
const deleteError = ref('')

const confirmDelete = (row) => {
  deleteTarget.value = row
  deleteError.value = ''
  showDeleteDialog.value = true
}

const handleDelete = async () => {
  deleteLoading.value = true
  try {
    await deleteSystem(deleteTarget.value.id)
    showDeleteDialog.value = false
    // Clear selection if deleted system was selected
    if (selectedSystemId.value === deleteTarget.value.id) {
      selectedSystemId.value = systems.value.find(s => s.id !== deleteTarget.value.id)?.id || null
    }
    deleteTarget.value = null
    await loadSystems()
    await loadDocuments()
    ElMessage.success('删除成功')
  } catch (err) {
    deleteError.value = err.response?.data?.message || '删除失败'
  } finally {
    deleteLoading.value = false
  }
}

const handleSystemCommand = (command, sys) => {
  if (command === 'edit') {
    openEditDialog(sys)
  } else if (command === 'delete') {
    confirmDelete(sys)
  }
}

// Document operations
const statusLabel = (s) => ({ PENDING: '待处理', PROCESSING: '处理中', SUCCESS: '完成', FAILED: '失败' }[s] || s)
const statusClass = (s) => ({ PENDING: 'status-pending', PROCESSING: 'status-processing', SUCCESS: 'status-done', FAILED: 'status-failed' }[s] || '')

const removeDocument = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除该文档吗？', '提示', { type: 'warning' })
    await http.delete(`/v1/documents/${row.id}`)
    documents.value = documents.value.filter(d => d.id !== row.id)
    if (!hasProcessing.value) stopPolling()
    ElMessage.success('删除成功')
  } catch (err) {
    if (err !== 'cancel') {
      console.error('删除文档失败', err)
      ElMessage.error('删除失败')
    }
  }
}

// Upload
const uploadModalVisible = ref(false)
const uploading = ref(false)
const isDragOver = ref(false)
const fileInputRef = ref(null)
const uploadForm = ref({ file: null })
const fileError = ref('')

const resetUploadForm = () => {
  uploadForm.value = { file: null }
  fileError.value = ''
  isDragOver.value = false
}

const triggerFileInput = () => fileInputRef.value?.click()

const validateFile = (file) => {
  if (!file) return '请选择文件'
  if (!file.name.match(/\.(md|markdown)$/i)) return '仅支持 .md 格式文件'
  if (file.size === 0) return '文件内容不能为空'
  return ''
}

const onFileChange = (e) => {
  const file = e.target.files?.[0]
  if (!file) return
  fileError.value = validateFile(file)
  uploadForm.value.file = fileError.value ? null : file
}

const onDrop = (e) => {
  isDragOver.value = false
  const file = e.dataTransfer.files?.[0]
  if (!file) return
  fileError.value = validateFile(file)
  uploadForm.value.file = fileError.value ? null : file
}

const confirmUpload = async () => {
  fileError.value = validateFile(uploadForm.value.file)
  if (fileError.value) return

  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', uploadForm.value.file)
    formData.append('systemId', selectedSystemId.value)
    await http.post('/v1/documents/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 120000  // 文件上传需要更长时间，2分钟超时
    })
    uploadModalVisible.value = false
    resetUploadForm()
    ElMessage.success('上传成功')
    // 重新加载文档列表以确保数据一致性
    await loadDocuments()
  } catch (err) {
    ElMessage.error(err.response?.data?.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

onMounted(() => {
  loadSystems()
})

onUnmounted(stopPolling)
</script>

<style scoped>
.page-container {
  height: 100%;
  display: flex;
  overflow: hidden;
}

/* System Sidebar */
.system-sidebar {
  width: 240px;
  min-width: 240px;
  background: var(--color-bg-card);
  border-right: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid var(--color-border-light);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.sidebar-header h3 {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0;
}

.system-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.system-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all 0.2s;
  margin-bottom: 2px;
}

.system-item:hover {
  background: var(--color-bg-hover);
}

.system-item.active {
  background: var(--color-sidebar-active);
}

.system-avatar {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: linear-gradient(135deg, #2563eb, #3b82f6);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  flex-shrink: 0;
}

.system-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.system-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.system-code {
  font-size: 11px;
  color: var(--color-text-tertiary);
  font-family: monospace;
}

.more-btn {
  opacity: 0;
  transition: opacity 0.2s;
}

.system-item:hover .more-btn {
  opacity: 1;
}

/* Main Content */
.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--color-bg-page);
}

.content-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 20px 24px;
  background: var(--color-bg-card);
  border-bottom: 1px solid var(--color-border);
}

.header-info {
  flex: 1;
  min-width: 0;
}

.system-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.system-title .title {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.system-desc {
  font-size: 13px;
  color: var(--color-text-muted);
  margin: 0;
  line-height: 1.5;
}

.content-body {
  flex: 1;
  padding: 20px 24px;
  overflow: auto;
}

.empty-system {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* Table Styles */
.data-table {
  background: var(--color-bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
}

.file-name-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.file-icon-wrapper {
  width: 36px;
  height: 36px;
  background: var(--color-primary-lighter);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.file-name {
  font-weight: 500;
  color: var(--color-text-primary);
}

.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;
}

.status-pending    { background: var(--color-bg-hover); color: var(--color-text-muted); }
.status-processing { background: var(--color-info-light); color: var(--color-primary); }
.status-done       { background: var(--color-success-light); color: #059669; }
.status-failed     { background: var(--color-error-light); color: #dc2626; }

.warning-text {
  font-size: 13px;
  color: var(--color-warning);
  margin-top: 8px;
}

.error-message {
  font-size: 13px;
  color: var(--color-error);
  margin-top: 8px;
}

/* Dialog Form */
.dialog-form :deep(.el-form-item__label) {
  font-weight: 500;
}

/* Upload Form */
.upload-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-secondary);
}

.form-label.required::before {
  content: '* ';
  color: var(--color-error);
}

.form-error {
  font-size: 12px;
  color: var(--color-error);
}

.upload-zone {
  border: 2px dashed var(--color-border);
  border-radius: var(--radius-lg);
  padding: 40px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
  background: var(--color-bg-subtle);
}

.upload-zone:hover,
.upload-zone.drag-over {
  border-color: var(--color-primary);
  background: var(--color-primary-lighter);
}

.upload-zone.has-file {
  border-color: var(--color-primary);
  background: var(--color-primary-lighter);
  border-style: solid;
}

.upload-icon {
  margin-bottom: 4px;
}

.upload-text {
  font-size: 14px;
  color: var(--color-text-secondary);
  font-weight: 500;
  margin: 0;
}

.upload-text.selected {
  color: var(--color-primary);
}

.upload-hint {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin: 0;
}
</style>
