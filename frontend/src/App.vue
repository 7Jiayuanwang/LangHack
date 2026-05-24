<template>
  <div id="app">
    <router-view />
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { onErrorCaptured } from 'vue'

const router = useRouter()

// 全局错误捕获 - 仅记录日志，不向用户弹出通用提示
// 各业务组件应自行处理错误并展示合适的反馈
onErrorCaptured((error, instance, info) => {
  console.error('捕获到组件错误:', {
    message: error.message,
    stack: error.stack,
    componentInfo: info,
    componentName: instance?.$options?.name || '未知组件'
  })
  // 返回 false 阻止错误继续传播，避免白屏
  return false
})

// 路由错误处理
router.onError((error) => {
  console.error('路由错误:', error)

  // 重复导航，忽略
  if (error.name === 'NavigationDuplicated') return

  // Vite 部署后旧 chunk / CSS 资源不存在，自动刷新加载新资源
  // 涵盖：Failed to fetch dynamically imported module / Unable to preload CSS
  if (
    error.message?.includes('Failed to fetch dynamically imported module') ||
    error.message?.includes('Importing a module script failed') ||
    error.message?.includes('Unable to preload CSS') ||
    error.message?.includes('dynamically imported module')
  ) {
    console.warn('检测到资源更新，正在刷新页面...')
    window.location.reload()
  }
  // 其他路由错误仅记录日志，不弹出提示（避免移动端被错误弹窗阻塞）
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
  --success-dark: #00A078;        /* 深成功绿 */
  --warning-color: #FFB347;       /* 警告金 */
  --warning-dark: #E6A23C;        /* 深警告金 */
  --error-color: #FF5757;         /* 错误红 */
  --error-dark: #E64242;          /* 深错误红 */
  --info-color: #4A90E2;          /* 信息蓝 */
  --info-dark: #3A7AD2;           /* 深信息蓝 */

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
  height: 100dvh;
  width: 100vw;
  /* 不设 overflow:hidden，避免阻断公开页（/articles 等）的原生滚动
     各布局组件自行管理内部滚动区域 */
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

/* student/Layout.vue 的 .trend-header 在文章页设置 pageTitle='' 时不需要显示，
   但其他 student 页面仍需要 header。
   改为通过 student Layout 内部的 scoped 样式控制，
   此全局规则改为只隐藏顶层 #app 下不属于任何 Layout 的裸 .trend-header（实际不存在）。
   注意：student/Layout.vue 的 .trend-header 受 scoped 作用域保护不受此影响，
   此规则已无实际作用，保留仅用于兼容旧遗留标记。 */
</style>
