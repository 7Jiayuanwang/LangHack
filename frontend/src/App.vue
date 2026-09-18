<template>
  <div id="app">
    <router-view v-slot="{ Component }">
      <keep-alive :include="cachedViews">
        <component :is="Component" />
      </keep-alive>
    </router-view>
    <NotificationPopup />
    <AgentCompanionWidget />
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { onErrorCaptured, onMounted, ref } from 'vue'
import NotificationPopup from '@/components/NotificationPopup.vue'
import AgentCompanionWidget from '@/components/AgentCompanionWidget.vue'

const router = useRouter()
const cachedViews = ref(['ArticlesHost'])

onMounted(() => {
  setTimeout(() => {
    if (typeof window !== 'undefined' && window.checkAchievements) {
      window.checkAchievements()
    }
  }, 2000)
})

onErrorCaptured((error, instance, info) => {
  console.error('Captured component error', { message: error.message, stack: error.stack, componentInfo: info, componentName: instance?.$options?.name || 'unknown' })
  return false
})

router.onError((error) => {
  console.error('Router error:', error)
  if (error.name === 'NavigationDuplicated') return
  if (error.message?.includes('Failed to fetch dynamically imported module') || error.message?.includes('Importing a module script failed') || error.message?.includes('Unable to preload CSS') || error.message?.includes('dynamically imported module')) {
    window.location.reload()
  }
})</script>

<style>
/* 娣遍們钃濈豢椋庢牸楂樼骇閰嶈壊 - 鐜颁唬鍟嗗姟 */
:root {
  /* 涓诲搧鐗岃壊 - 娣遍們钃濈豢 */
  --primary-color: #0066CC;       /* 娣辫摑 */
  --primary-light: #0088EE;       /* 娴呰摑 */
  --primary-dark: #0055A5;        /* 娣辨捣钃?*/
  --primary-gradient: linear-gradient(135deg, #0066CC 0%, #00A8CC 50%, #00C4A7 100%);

  /* 鐜颁唬杈呭姪鑹?*/
  --accent-color: #00A8CC;        /* 闈掕摑鑹?*/
  --secondary-color: #00C4A7;      /* 闈掔豢鑹?*/
  --vibrant-teal: #00E5CC;         /* 椴滈潚缁?*/
  --soft-blue: #4A90E2;           /* 鏌斿拰钃?*/

  /* 鐜颁唬鍔熻兘鑹?*/
  --success-color: #00C896;       /* 鎴愬姛缁?*/
  --success-dark: #00A078;        /* 娣辨垚鍔熺豢 */
  --warning-color: #FFB347;       /* 璀﹀憡閲?*/
  --warning-dark: #E6A23C;        /* 娣辫鍛婇噾 */
  --error-color: #FF5757;         /* 閿欒绾?*/
  --error-dark: #E64242;          /* 娣遍敊璇孩 */
  --info-color: #4A90E2;          /* 淇℃伅钃?*/
  --info-dark: #3A7AD2;           /* 娣变俊鎭摑 */

  /* 娣辫壊鑳屾櫙鑹茬郴 */
  --bg-primary: #FFFFFF;          /* 绾櫧 */
  --bg-secondary: #F7FAFC;        /* 鏋佹祬钃濈伆 */
  --bg-tertiary: #F1F5F9;         /* 娴呰摑鐏?*/
  --bg-overlay: rgba(0, 10, 20, 0.45); /* 娣辫壊閬僵 */
  --bg-modal: rgba(255, 255, 255, 0.98); /* 妯℃€佽儗鏅?*/

  /* 娣辫壊鏂囧瓧鑹茬郴 */
  --text-primary: #0F172A;        /* 娣辫摑鐏颁富鏂囧瓧 */
  --text-secondary: #475569;      /* 钃濈伆娆℃枃瀛?*/
  --text-tertiary: #64748B;       /* 钃濈伆涓夌骇鏂囧瓧 */
  --text-quaternary: #94A3B8;     /* 娴呰摑鐏板洓绾ф枃瀛?*/
  --text-placeholder: #CBD5E1;    /* 娴呰摑鐏板崰浣嶇 */
  --text-on-primary: #FFFFFF;     /* 涓昏壊涓婃枃瀛?*/
  --text-accent: var(--primary-color); /* 寮鸿皟鏂囧瓧 */

  /* 娣辫壊杈规绯荤粺 */
  --border-color: #E2E8F0;        /* 钃濈伆鏍囧噯杈规 */
  --border-light: #F1F5F9;        /* 钃濈伆娴呰竟妗?*/
  --border-dark: #CBD5E1;         /* 钃濈伆娣辫竟妗?*/
  --separator-color: #F8FAFC;     /* 娴呰摑鐏板垎鍓茬嚎 */

  /* 娣辫壊闃村奖绯荤粺 - 鐜颁唬鍟嗗姟 */
  --shadow-sm: 0 1px 2px 0 rgba(0, 10, 20, 0.06);
  --shadow-md: 0 4px 6px -1px rgba(0, 10, 20, 0.12), 0 2px 4px -1px rgba(0, 10, 20, 0.08);
  --shadow-lg: 0 10px 15px -3px rgba(0, 10, 20, 0.15), 0 4px 6px -2px rgba(0, 10, 20, 0.10);
  --shadow-xl: 0 20px 25px -5px rgba(0, 10, 20, 0.18), 0 10px 10px -5px rgba(0, 10, 20, 0.12);
  --shadow-brand: 0 4px 20px rgba(0, 102, 204, 0.25);
  --shadow-nav: 0 -2px 10px rgba(0, 10, 20, 0.08);

  /* 娣辫壊娓愬彉绯荤粺 */
  --gradient-brand: linear-gradient(135deg, #0066CC 0%, #00A8CC 100%);
  --gradient-hero: linear-gradient(135deg, #0066CC 0%, #00A8CC 50%, #00C4A7 100%);
  --gradient-subtle: linear-gradient(180deg, #FFFFFF 0%, #F7FAFC 100%);
  --gradient-overlay: linear-gradient(180deg, rgba(255, 255, 255, 0.98) 0%, rgba(247, 250, 252, 0.95) 100%);

  /* 鐗规畩鏁堟灉 */
  --glass-bg: rgba(255, 255, 255, 0.8);
  --glass-border: rgba(255, 255, 255, 0.18);

  /* 鍏煎鍙橀噺 - Admin/Teacher Layout 寮曠敤 */
  --surface-color: #FFFFFF;                                               /* 绛変环 --bg-primary */
  --border-medium: #CBD5E1;                                               /* 绛変环 --border-dark */
  --shadow-light: 0 1px 3px 0 rgba(0, 10, 20, 0.08);                    /* 杞婚槾褰?*/
  --gradient-accent: linear-gradient(135deg, #0066CC 0%, #00A8CC 100%); /* Admin 鏍囬娓愬彉 */
  --gradient-secondary: linear-gradient(135deg, #00A8CC 0%, #00C4A7 100%); /* Teacher 鏍囬娓愬彉 */
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
  /* 涓嶈 overflow:hidden锛岄伩鍏嶉樆鏂叕寮€椤碉紙/articles 绛夛級鐨勫師鐢熸粴鍔?     鍚勫竷灞€缁勪欢鑷绠＄悊鍐呴儴婊氬姩鍖哄煙 */
  background: var(--bg-secondary);
}

/* 婊氬姩鏉?*/
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

/* 杩囨浮鍔ㄧ敾 */
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

/* student/Layout.vue 鐨?.trend-header 鍦ㄦ枃绔犻〉璁剧疆 pageTitle='' 鏃朵笉闇€瑕佹樉绀猴紝
   浣嗗叾浠?student 椤甸潰浠嶉渶瑕?header銆?   鏀逛负閫氳繃 student Layout 鍐呴儴鐨?scoped 鏍峰紡鎺у埗锛?   姝ゅ叏灞€瑙勫垯鏀逛负鍙殣钘忛《灞?#app 涓嬩笉灞炰簬浠讳綍 Layout 鐨勮８ .trend-header锛堝疄闄呬笉瀛樺湪锛夈€?   娉ㄦ剰锛歴tudent/Layout.vue 鐨?.trend-header 鍙?scoped 浣滅敤鍩熶繚鎶や笉鍙楁褰卞搷锛?   姝よ鍒欏凡鏃犲疄闄呬綔鐢紝淇濈暀浠呯敤浜庡吋瀹规棫閬楃暀鏍囪銆?*/
</style>
