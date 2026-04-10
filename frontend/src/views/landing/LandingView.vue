<template>
  <div class="landing-page">
    <!-- Top Navigation Bar -->
    <header class="landing-header">
      <div class="header-content">
        <div class="logo">
          <div class="logo-icon">
            <el-icon :size="22"><Collection /></el-icon>
          </div>
          <span class="logo-text">CoExistree</span>
        </div>
        <el-button type="primary" size="default" @click="goToLogin" class="login-btn">
          <el-icon :size="16"><User /></el-icon>
          <span>登录</span>
        </el-button>
      </div>
    </header>

    <!-- Hero Section -->
    <section class="hero-section">
      <div class="hero-content">
        <div class="hero-badge">
          <el-icon :size="14"><CircleCheckFilled /></el-icon>
          <span>AI 驱动的智能知识管理</span>
        </div>
        <h1 class="hero-title">
          让知识共生<br/>让智能生长
        </h1>
        <p class="hero-desc">
          CoExistree 是基于 AI 的知识库与智能问答平台，帮助团队高效管理文档、快速解答问题，让知识创造更简单。
        </p>
        <div class="hero-actions">
          <el-button type="primary" size="large" @click="goToLogin" class="cta-btn">
            立即开始
            <el-icon :size="18"><ArrowRight /></el-icon>
          </el-button>
          <el-button size="large" plain @click="scrollToFeatures" class="learn-more-btn">
            了解更多
          </el-button>
        </div>
      </div>
      <div class="hero-visual">
        <div class="visual-card">
          <div class="visual-icon">
            <el-icon :size="48"><Reading /></el-icon>
          </div>
          <div class="visual-stat">
            <span class="stat-number">10x</span>
            <span class="stat-label">知识检索效率提升</span>
          </div>
          <div class="visual-divider"></div>
          <div class="visual-stat">
            <span class="stat-number">24/7</span>
            <span class="stat-label">AI 智能问答在线</span>
          </div>
        </div>
      </div>
    </section>

    <!-- Features Section -->
    <section class="features-section" ref="featuresRef">
      <div class="features-header">
        <h2 class="features-title">核心功能</h2>
        <p class="features-desc">一站式知识管理平台，满足团队的各种需求</p>
      </div>
      <div class="features-grid">
        <div class="feature-card" v-for="feature in features" :key="feature.title">
          <div class="feature-icon" :class="feature.iconClass">
            <el-icon :size="28"><component :is="feature.icon" /></el-icon>
          </div>
          <h3 class="feature-title">{{ feature.title }}</h3>
          <p class="feature-desc">{{ feature.description }}</p>
        </div>
      </div>
    </section>

    <!-- Footer -->
    <footer class="landing-footer">
      <div class="footer-content">
        <span>CoExistree &copy; {{ new Date().getFullYear() }}</span>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { ref } from 'vue'
import {
  Collection,
  User,
  ArrowRight,
  Reading,
  Document,
  ChatDotRound,
  Search,
  Lock,
  TrendCharts
} from '@element-plus/icons-vue'

const router = useRouter()
const featuresRef = ref(null)

const features = [
  {
    title: '智能知识库',
    description: '集中管理团队文档与知识资产，支持多格式文档上传、分类检索与版本管理，让知识井井有条。',
    icon: Document,
    iconClass: 'icon-blue'
  },
  {
    title: 'AI 智能问答',
    description: '基于大语言模型的智能问答系统，自动从知识库中提取答案，7x24 小时在线解答您的疑问。',
    icon: ChatDotRound,
    iconClass: 'icon-green'
  },
  {
    title: '精准搜索',
    description: '强大的语义搜索能力，不再局限于关键词匹配，理解您的意图，返回最相关的知识内容。',
    icon: Search,
    iconClass: 'icon-purple'
  },
  {
    title: '权限管理',
    description: '细粒度的权限控制体系，支持角色管理与系统级权限分配，确保知识安全可控。',
    icon: Lock,
    iconClass: 'icon-orange'
  },
  {
    title: '数据分析',
    description: '可视化展示知识库使用情况、问答统计与系统运行指标，帮助管理者持续优化知识运营。',
    icon: TrendCharts,
    iconClass: 'icon-teal'
  }
]

const goToLogin = () => {
  router.push('/login')
}

const scrollToFeatures = () => {
  featuresRef.value?.scrollIntoView({ behavior: 'smooth' })
}
</script>

<style scoped>
.landing-page {
  min-height: 100vh;
  height: 100vh;
  background: var(--color-bg-page);
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

/* Header */
.landing-header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-bottom: 1px solid rgba(226, 232, 240, 0.6);
}

.header-content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 var(--space-6);
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logo-icon {
  width: 34px;
  height: 34px;
  background: linear-gradient(135deg, #2563eb, #3b82f6);
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
}

.logo-text {
  font-size: 18px;
  font-weight: 700;
  color: var(--color-text-primary);
  letter-spacing: -0.5px;
}

.login-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 90px;
  cursor: pointer;
}

.login-btn:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

/* Hero Section */
.hero-section {
  flex: 1;
  padding: 160px var(--space-6) 80px;
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  gap: 80px;
  width: 100%;
}

.hero-content {
  flex: 1;
  max-width: 560px;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  background: var(--color-primary-lighter);
  color: var(--color-primary);
  border-radius: 20px;
  font-size: 13px;
  font-weight: 500;
  margin-bottom: var(--space-6);
}

.hero-title {
  font-size: 48px;
  font-weight: 700;
  color: var(--color-text-primary);
  line-height: 1.2;
  margin: 0 0 var(--space-5) 0;
  letter-spacing: -1px;
}

.hero-desc {
  font-size: 18px;
  line-height: 1.7;
  color: var(--color-text-secondary);
  margin: 0 0 var(--space-8) 0;
}

.hero-actions {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.cta-btn {
  padding: 14px 28px;
  font-size: 16px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.cta-btn:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-lg);
}

.cta-btn:active {
  transform: translateY(0);
  transition-duration: 0.1s;
}

.cta-btn:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

.learn-more-btn {
  padding: 14px 28px;
  font-size: 16px;
  border-radius: var(--radius-lg);
  cursor: pointer;
  background: transparent;
  border: 1px solid transparent;
  color: var(--color-text-secondary);
  transition: color 0.2s ease, background 0.2s ease;
}

.learn-more-btn:hover {
  background: var(--color-bg-hover);
  color: var(--color-text-primary);
}

.learn-more-btn:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

/* Hero Visual */
.hero-visual {
  flex: 0 0 auto;
}

.visual-card {
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-radius: var(--radius-xl);
  padding: var(--space-8);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.06), 0 2px 8px rgba(0, 0, 0, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.5);
  display: flex;
  align-items: center;
  gap: var(--space-6);
  min-width: 280px;
  animation: fadeSlideUp 0.6s ease-out both;
  animation-delay: 0.3s;
}

.visual-icon {
  width: 80px;
  height: 80px;
  background: linear-gradient(135deg, var(--color-primary-light), var(--color-primary-lighter));
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-primary);
  flex-shrink: 0;
}

.visual-stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.stat-number {
  font-size: 28px;
  font-weight: 700;
  color: var(--color-text-primary);
}

.stat-label {
  font-size: 13px;
  color: var(--color-text-muted);
}

.visual-divider {
  width: 1px;
  height: 48px;
  background: var(--color-border);
  align-self: center;
}

/* Features Section */
.features-section {
  padding: 80px var(--space-6);
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
}

.features-header {
  text-align: center;
  margin-bottom: 56px;
}

.features-title {
  font-size: 32px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 0 0 var(--space-3) 0;
  letter-spacing: -0.5px;
}

.features-desc {
  font-size: 16px;
  color: var(--color-text-secondary);
  margin: 0;
}

.features-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-6);
}

.feature-card {
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  border-radius: var(--radius-lg);
  padding: var(--space-8);
  border: 1px solid rgba(226, 232, 240, 0.5);
  transition: transform 0.25s ease, box-shadow 0.25s ease, border-color 0.25s ease;
  cursor: default;
}

.feature-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.06), 0 2px 6px rgba(0, 0, 0, 0.04);
  border-color: var(--color-primary-light);
}

.feature-icon {
  width: 56px;
  height: 56px;
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: var(--space-5);
}

.icon-blue {
  background: var(--color-primary-lighter);
  color: var(--color-primary);
}

.icon-green {
  background: var(--color-success-light);
  color: var(--color-success);
}

.icon-purple {
  background: #f3e8ff;
  color: #9333ea;
}

.icon-orange {
  background: var(--color-warning-light);
  color: var(--color-warning);
}

.icon-teal {
  background: #ccfbf1;
  color: #0d9488;
}

.feature-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0 0 var(--space-2) 0;
}

.feature-desc {
  font-size: 14px;
  line-height: 1.7;
  color: var(--color-text-secondary);
  margin: 0;
}

/* Footer */
.landing-footer {
  padding: var(--space-6);
  border-top: 1px solid var(--color-border-light);
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}

.footer-content {
  max-width: 1200px;
  margin: 0 auto;
  text-align: center;
  font-size: 13px;
  color: var(--color-text-tertiary);
}

/* Responsive */
@media (max-width: 1024px) {
  .hero-section {
    flex-direction: column;
    text-align: center;
    gap: 48px;
    padding-top: 120px;
  }

  .hero-content {
    max-width: 100%;
  }

  .hero-actions {
    justify-content: center;
  }

  .features-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .hero-title {
    font-size: 36px;
  }

  .hero-desc {
    font-size: 16px;
  }

  .hero-actions {
    flex-direction: column;
    width: 100%;
  }

  .cta-btn,
  .learn-more-btn {
    width: 100%;
    justify-content: center;
  }

  .features-grid {
    grid-template-columns: 1fr;
  }

  .visual-card {
    flex-direction: column;
    min-width: auto;
    width: 100%;
  }

  .visual-divider {
    width: 100%;
    height: 1px;
  }
}

/* Animations */
@keyframes fadeSlideUp {
  from {
    opacity: 0;
    transform: translateY(16px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.hero-content {
  animation: fadeSlideUp 0.5s ease-out both;
}

.features-section {
  animation: fadeSlideUp 0.5s ease-out both;
  animation-delay: 0.15s;
}

/* Accessibility: Reduced Motion */
@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }
}
</style>
