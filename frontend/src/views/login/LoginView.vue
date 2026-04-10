<template>
  <div class="login-page">
    <!-- Left Side: Hero/Brand -->
    <div class="login-brand">
      <div class="brand-content">
        <div class="brand-logo">
          <div class="brand-logo-icon">
            <el-icon :size="32"><Collection /></el-icon>
          </div>
          <h1 class="brand-title">CoExistree</h1>
        </div>
        <p class="brand-desc">
          AI 驱动的智能知识管理平台<br/>让知识共生，让智能生长
        </p>
        <div class="brand-features">
          <div class="brand-feature" v-for="item in brandFeatures" :key="item.text">
            <el-icon :size="18" class="brand-feature-icon"><component :is="item.icon" /></el-icon>
            <span>{{ item.text }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Right Side: Login Form -->
    <div class="login-form-area">
      <div class="login-form-wrapper">
        <div class="form-header">
          <h2>欢迎回来</h2>
          <p>请输入您的账号信息登录</p>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          class="login-form"
          @submit.prevent="handleLogin"
          size="large"
        >
          <el-form-item prop="username">
            <el-input
              v-model="form.username"
              placeholder="请输入用户名"
              :prefix-icon="User"
              autocomplete="username"
            />
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              :prefix-icon="Lock"
              show-password
              autocomplete="current-password"
              @keyup.enter="handleLogin"
            />
          </el-form-item>

          <el-form-item>
            <el-button
              type="primary"
              class="submit-btn"
              :loading="loading"
              @click="handleLogin"
              size="large"
            >
              {{ loading ? '登录中...' : '登 录' }}
            </el-button>
          </el-form-item>
        </el-form>

        <p class="form-footer">
          还没有账号？请联系管理员开通
        </p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Collection,
  User,
  Lock,
  Document,
  ChatDotRound,
  TrendCharts
} from '@element-plus/icons-vue'
import { login } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ]
}

const brandFeatures = [
  { text: '智能知识库管理', icon: Document },
  { text: 'AI 智能问答', icon: ChatDotRound },
  { text: '数据分析与洞察', icon: TrendCharts }
]

const handleLogin = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const res = await login(form)
    if (res.success) {
      authStore.setAuth(res.data)
      ElMessage.success('登录成功')
      router.push('/app/chat')
    } else {
      ElMessage.error(res.message || '登录失败')
    }
  } catch (err) {
    ElMessage.error(err.response?.data?.message || '登录失败，请检查用户名和密码')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  display: flex;
  min-height: 100vh;
  background: white;
}

/* Left Brand Area */
.login-brand {
  flex: 1;
  background: linear-gradient(135deg, #1e3a5f 0%, #2563eb 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-8);
  position: relative;
  overflow: hidden;
}

.login-brand::before {
  content: '';
  position: absolute;
  top: -50%;
  right: -20%;
  width: 500px;
  height: 500px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 50%;
}

.login-brand::after {
  content: '';
  position: absolute;
  bottom: -30%;
  left: -10%;
  width: 400px;
  height: 400px;
  background: rgba(255, 255, 255, 0.03);
  border-radius: 50%;
}

.brand-content {
  max-width: 440px;
  position: relative;
  z-index: 1;
  color: white;
}

.brand-logo {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 48px;
}

.brand-logo-icon {
  width: 52px;
  height: 52px;
  background: rgba(255, 255, 255, 0.15);
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  backdrop-filter: blur(8px);
}

.brand-title {
  font-size: 32px;
  font-weight: 700;
  margin: 0;
  letter-spacing: -0.5px;
}

.brand-desc {
  font-size: 18px;
  line-height: 1.7;
  opacity: 0.9;
  margin: 0 0 48px 0;
}

.brand-features {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.brand-feature {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  font-size: 15px;
  opacity: 0.9;
}

.brand-feature-icon {
  flex-shrink: 0;
  opacity: 0.8;
}

/* Right Form Area */
.login-form-area {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-8);
  background: white;
}

.login-form-wrapper {
  width: 100%;
  max-width: 400px;
}

.form-header {
  margin-bottom: 40px;
}

.form-header h2 {
  font-size: 28px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 0 0 var(--space-2) 0;
  letter-spacing: -0.5px;
}

.form-header p {
  font-size: 15px;
  color: var(--color-text-muted);
  margin: 0;
}

.login-form {
  margin-bottom: var(--space-6);
}

.login-form .el-form-item {
  margin-bottom: var(--space-5);
}

.login-form :deep(.el-input__wrapper) {
  padding: 12px 16px;
  border-radius: var(--radius-md);
  box-shadow: none;
  border: 1px solid var(--color-border);
  transition: all 0.2s ease;
}

.login-form :deep(.el-input__wrapper:hover) {
  border-color: var(--color-primary);
}

.login-form :deep(.el-input__wrapper.is-focus) {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-primary-lighter);
}

.submit-btn {
  width: 100%;
  height: 48px;
  font-size: 16px;
  font-weight: 600;
  border-radius: var(--radius-md);
  margin-top: var(--space-3);
  letter-spacing: 2px;
}

.form-footer {
  text-align: center;
  font-size: 14px;
  color: var(--color-text-tertiary);
  margin: 0;
}

/* Responsive */
@media (max-width: 1024px) {
  .login-brand {
    display: none;
  }

  .login-form-area {
    background: var(--color-bg-page);
  }

  .login-form-wrapper {
    background: white;
    padding: var(--space-8);
    border-radius: var(--radius-lg);
    box-shadow: var(--shadow-md);
  }

  .form-header {
    text-align: center;
  }

  .form-header::before {
    content: '';
    display: block;
    width: 48px;
    height: 48px;
    background: linear-gradient(135deg, #2563eb, #3b82f6);
    border-radius: var(--radius-md);
    margin: 0 auto var(--space-4);
  }

  .form-header h2::before {
    content: 'CoExistree';
    display: block;
    font-size: 16px;
    font-weight: 600;
    color: var(--color-primary);
    margin-bottom: var(--space-2);
    letter-spacing: 0;
  }
}

@media (max-width: 480px) {
  .login-form-area {
    padding: var(--space-4);
  }

  .login-form-wrapper {
    padding: var(--space-6);
  }

  .form-header h2 {
    font-size: 24px;
  }
}
</style>
