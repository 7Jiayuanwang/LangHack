<template>
  <div id="app">
    <router-view />
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { onErrorCaptured } from 'vue'

const router = useRouter()

// 全局错误捕获 - 捕获组件中未处理的异常
onErrorCaptured((error, instance, info) => {
  console.error('捕获到组件错误:', {
    message: error.message,
    stack: error.stack,
    componentInfo: info,
    componentName: instance?.$options.name || '未知组件'
  })

  // 显示通用错误提示
  ElMessage.error('发生错误，请刷新页面后重试')

  // 返回 false 阻止错误继续传播，避免白屏
  return false
})

// 路由错误处理
router.onError((error) => {
  console.error('路由错误:', error)
  
  // 处理特定的路由错误
  if (error.name === 'NavigationDuplicated') {
    // 重复导航，忽略
    return
  }
  
  // 其他路由错误
  if (error.message) {
    ElMessage.error('页面导航异常: ' + error.message)
  }
})
</script>

<style>
/* 深邃蓝绿风格高级配色 - 现代商务 */
:root {
  /* 主品牌色 - 深邃蓝绿 */
  --primary-color: #0066CC;       /* 深蓝 */
  --primary-light: #0088EE;       /* 浅蓝 */
  --primary-dark: #0055A5;        /* 深海蓝 */
  --primary-gradient: linear-gradient(135deg, #0066CC 0%, #00A8CC 50%, #00C4A7 100%);

  /* 现代辅助色 */
  --accent-color: #00A8CC;        /* 青蓝色 */
  --secondary-color: #00C4A7;      /* 青绿色 */
  --vibrant-teal: #00E5CC;         /* 鲜青绿 */
  --soft-blue: #4A90E2;           /* 柔和蓝 */

  /* 现代功能色 */
  --success-color: #00C896;       /* 成功绿 */
  --warning-color: #FFB347;       /* 警告金 */
  --error-color: #FF5757;         /* 错误红 */
  --info-color: #4A90E2;          /* 信息蓝 */

  /* 深色背景色系 */
  --bg-primary: #FFFFFF;          /* 纯白 */
  --bg-secondary: #F7FAFC;        /* 极浅蓝灰 */
  --bg-tertiary: #F1F5F9;         /* 浅蓝灰 */
  --bg-overlay: rgba(0, 10, 20, 0.45); /* 深色遮罩 */
  --bg-modal: rgba(255, 255, 255, 0.98); /* 模态背景 */

  /* 深色文字色系 */
  --text-primary: #0F172A;        /* 深蓝灰主文字 */
  --text-secondary: #475569;      /* 蓝灰次文字 */
  --text-tertiary: #64748B;       /* 蓝灰三级文字 */
  --text-quaternary: #94A3B8;     /* 浅蓝灰四级文字 */
  --text-placeholder: #CBD5E1;    /* 浅蓝灰占位符 */
  --text-on-primary: #FFFFFF;     /* 主色上文字 */
  --text-accent: var(--primary-color); /* 强调文字 */

  /* 深色边框系统 */
  --border-color: #E2E8F0;        /* 蓝灰标准边框 */
  --border-light: #F1F5F9;        /* 蓝灰浅边框 */
  --border-dark: #CBD5E1;         /* 蓝灰深边框 */
  --separator-color: #F8FAFC;     /* 浅蓝灰分割线 */

  /* 深色阴影系统 - 现代商务 */
  --shadow-sm: 0 1px 2px 0 rgba(0, 10, 20, 0.06);
  --shadow-md: 0 4px 6px -1px rgba(0, 10, 20, 0.12), 0 2px 4px -1px rgba(0, 10, 20, 0.08);
  --shadow-lg: 0 10px 15px -3px rgba(0, 10, 20, 0.15), 0 4px 6px -2px rgba(0, 10, 20, 0.10);
  --shadow-xl: 0 20px 25px -5px rgba(0, 10, 20, 0.18), 0 10px 10px -5px rgba(0, 10, 20, 0.12);
  --shadow-brand: 0 4px 20px rgba(0, 102, 204, 0.25);
  --shadow-nav: 0 -2px 10px rgba(0, 10, 20, 0.08);

  /* 深色渐变系统 */
  --gradient-brand: linear-gradient(135deg, #0066CC 0%, #00A8CC 100%);
  --gradient-hero: linear-gradient(135deg, #0066CC 0%, #00A8CC 50%, #00C4A7 100%);
  --gradient-subtle: linear-gradient(180deg, #FFFFFF 0%, #F7FAFC 100%);
  --gradient-overlay: linear-gradient(180deg, rgba(255, 255, 255, 0.98) 0%, rgba(247, 250, 252, 0.95) 100%);

  /* 特殊效果 */
  --glass-bg: rgba(255, 255, 255, 0.8);
  --glass-border: rgba(255, 255, 255, 0.18);

  /* 兼容变量 - Admin/Teacher Layout 引用 */
  --surface-color: #FFFFFF;                                               /* 等价 --bg-primary */
  --border-medium: #CBD5E1;                                               /* 等价 --border-dark */
  --shadow-light: 0 1px 3px 0 rgba(0, 10, 20, 0.08);                    /* 轻阴影 */
  --gradient-accent: linear-gradient(135deg, #0066CC 0%, #00A8CC 100%); /* Admin 标题渐变 */
  --gradient-secondary: linear-gradient(135deg, #00A8CC 0%, #00C4A7 100%); /* Teacher 标题渐变 */
}

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body {
  height: 100%;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  background: var(--bg-secondary);
  color: var(--text-primary);
  line-height: 1.5;
}

#app {
  height: 100vh;
  width: 100vw;
  overflow: hidden;
  background: var(--bg-secondary);
}

/* 滚动条 */
::-webkit-scrollbar {
  width: 4px;
}

::-webkit-scrollbar-track {
  background: transparent;
}

::-webkit-scrollbar-thumb {
  background: var(--separator-color);
  border-radius: 2px;
}

/* 过渡动画 */
.fade-enter-active, .fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from, .fade-leave-to {
  opacity: 0;
}

.slide-up-enter-active, .slide-up-leave-active {
  transition: all 0.3s ease;
}

.slide-up-enter-from {
  transform: translateY(20px);
  opacity: 0;
}

.slide-up-leave-to {
  transform: translateY(-20px);
  opacity: 0;
}

.trend-header {
  display: none;
}
</style>
