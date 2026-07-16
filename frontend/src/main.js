import { createApp } from "vue";
import { createPinia } from "pinia";
import ElementPlus from "element-plus";
import zhCn from "element-plus/dist/locale/zh-cn.mjs";
import "element-plus/dist/index.css";
import * as ElementPlusIconsVue from "@element-plus/icons-vue";
import App from "./App.vue";
import router from "./router";
import { clearAuthStorage, getAuthHeader, refreshOnAppStart } from "@/utils/auth";
import { environment } from "@/utils/environment";
import "./styles/mobile.scss";

const app = createApp(App);

// 环境检测
const isDevelopment = import.meta.env.DEV;
const isProduction = import.meta.env.PROD;

const originalFetch = window.fetch.bind(window);

window.fetch = async (input, init = {}) => {
  const requestUrl = typeof input === "string" ? input : input?.url || "";
  const resolvedUrl =
    typeof requestUrl === "string" && requestUrl
      ? environment.resolveApiUrl(requestUrl)
      : requestUrl;
  const isApiRequest = environment.isApiRequestUrl(resolvedUrl);
  const resolvedInput =
    typeof input === "string"
      ? resolvedUrl
      : input instanceof Request && resolvedUrl && resolvedUrl !== input.url
        ? new Request(resolvedUrl, input)
        : input;

  let nextInit = init;
  let hadAuthHeader = false;

  if (isApiRequest) {
    const headers = new Headers(
      init.headers ||
        (typeof input !== "string" ? input.headers : undefined) ||
        {},
    );
    hadAuthHeader = headers.has("Authorization");
    if (!hadAuthHeader) {
      const authHeader = getAuthHeader();
      if (authHeader) {
        headers.set("Authorization", authHeader);
        hadAuthHeader = true;
      }
    }
    nextInit = { ...init, headers };
  }

  const response = await originalFetch(resolvedInput, nextInit);

  if (
    isApiRequest &&
    hadAuthHeader &&
    response.status === 401 &&
    !resolvedUrl.includes("/api/auth/login")
  ) {
    try {
      clearAuthStorage();
      sessionStorage.removeItem("elevatedRole");
      sessionStorage.removeItem("elevationExpiresAt");
    } catch (e) {
      console.warn("Failed to clear auth state after fetch 401:", e);
    }
    window.location.replace("/login");
  }

  return response;
};

// 错误收集器（生产环境可上报）
const errorCollector = {
  errors: [],
  warnings: [],

  collectError(error, context = {}) {
    const errorInfo = {
      timestamp: new Date().toISOString(),
      message: error?.message || String(error),
      stack: error?.stack,
      context,
      url: window.location.href,
    };
    this.errors.push(errorInfo);

    // 生产环境上报错误（可对接到错误监控系统）
    if (isProduction) {
      this.reportError(errorInfo);
    }

    // 开发环境保留最近 100 条错误
    if (this.errors.length > 100) {
      this.errors.shift();
    }
  },

  collectWarning(message, context = {}) {
    const warningInfo = {
      timestamp: new Date().toISOString(),
      message,
      context,
    };
    this.warnings.push(warningInfo);

    // 开发环境保留最近 50 条警告
    if (this.warnings.length > 50) {
      this.warnings.shift();
    }
  },

  reportError(errorInfo) {
    try {
      // Send to backend error log endpoint
      navigator.sendBeacon(
        environment.resolveApiUrl("/api/errors/report"),
        new Blob([JSON.stringify(errorInfo)], { type: "application/json" }),
      );
    } catch (e) {
      // 错误上报失败不应中断应用
      console.warn("Failed to report error:", e);
    }
  },

  getErrors() {
    return this.errors;
  },

  getWarnings() {
    return this.warnings;
  },
};

// 挂载到全局，便于调试
if (isDevelopment) {
  window.__errorCollector = errorCollector;
}

// 注册所有 Element Plus 图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component);
}

// 按正确顺序注册插件
// 1. 状态管理（优先级最高）
app.use(createPinia());

// 2. UI 框架
app.use(ElementPlus, {
  locale: zhCn, // 配置中文语言包
});

// 3. 路由（依赖状态管理和 UI 框架）
app.use(router);

// 全局错误处理器
app.config.errorHandler = (err, instance, info) => {
  const errorContext = {
    componentName: instance?.$options?.name || "Unknown",
    info,
  };

  errorCollector.collectError(err, errorContext);

  // 开发环境详细输出
  if (isDevelopment) {
    console.error("Vue Error:", err);
    console.error("Component:", instance);
    console.error("Info:", info);
  } else {
    // 生产环境简化输出
    console.error("An error occurred in the application");
  }

  // 注意：返回 true 会阻止错误继续传播到全局错误处理
  return false;
};

// 全局警告处理器（Vue 运行时警告）
app.config.warnHandler = (message, instance, trace) => {
  const warningContext = {
    componentName: instance?.$options?.name || "Unknown",
    trace,
  };

  errorCollector.collectWarning(message, warningContext);

  // 开发环境输出详细信息
  if (isDevelopment) {
    console.warn("Vue Warning:", message);
    console.warn("Component:", instance?.$options?.name);
    console.warn("Trace:", trace);
  }
};

// 全局未捕获异常处理
window.addEventListener("error", (event) => {
  errorCollector.collectError(event.error, {
    type: "uncaught_error",
    filename: event.filename,
    lineno: event.lineno,
    colno: event.colno,
  });
});

// 处理未捕获的 Promise 拒绝
window.addEventListener("unhandledrejection", (event) => {
  errorCollector.collectError(event.reason, {
    type: "unhandled_rejection",
  });
});

// 挂载应用
app.mount("#app");

// 应用启动后主动刷新一次 refresh token，重置 7 天有效期（不阻塞挂载）

const happySentence = [
"太棒啦，学习进度又前进一大步✨",
"坚持超难得，你真的很自律！",
"今日努力已达标，继续保持状态",
"单词/任务稳稳拿下，越来越厉害",
"认真学习的样子超亮眼~",
"星光不负赶路人，你超优秀",
"慢慢来，你走的每一步都算数",
"今日蓄力，来日一鸣惊人",
"小小成就，大大成长",
"持之以恒，终有所成",
"日积月累，终将质变",
"愿你提笔从容自信，合笔如愿以偿"
];
// 全局标记：防止同时弹出多个成就弹窗
let isShowingAchieve = false;

function getRandText(){
    return happySentence[Math.floor(Math.random()*happySentence.length)];
}

function makeStar(boxDom){
    for(let i=0;i<18;i++){
        let s=document.createElement('div');
        s.className='star-item';
        s.style.setProperty('--tx',(Math.random()-0.5)*130+'px');
        s.style.setProperty('--ty',(Math.random()-0.5)*130+'px');
        boxDom.appendChild(s);
        setTimeout(()=>s.remove(),1300);
    }
}

function showAchieve(title,desc){
    // 判断：如果已有弹窗正在展示，直接终止，不重复弹出
    if(isShowingAchieve) return;
    isShowingAchieve = true;

    let mask=document.createElement('div');
    mask.className='achievement-mask';
    let box=document.createElement('div');
    box.className='ach-box';
    box.innerHTML=`<div class="ach-title">${title}</div><div class="ach-desc">${desc}</div><div class="ach-happy">${getRandText()}</div>`;
    mask.appendChild(box);
    document.body.appendChild(mask);
    mask.style.display='flex';

    // 新增：点击遮罩黑色区域手动关闭弹窗
    mask.addEventListener('click', (e) => {
        // 点击弹窗内部不关闭，只点击外层黑色遮罩才关闭
        if(e.target === mask){
            mask.remove();
            isShowingAchieve = false;
        }
    });

    makeStar(box);
    // 计时自动关闭，重置弹窗状态标记
    setTimeout(()=>{
        mask.remove();
        isShowingAchieve = false;
    },2900);
}

// 所有成就触发函数挂载到window，全局任意Vue组件直接调用
window.taskFinishAchieve = () => showAchieve("任务提交成功✅","本次学习任务圆满完成");
window.firstStudy = () => showAchieve("初入学海","完成第一次线上学习任务");
window.day1 = () => showAchieve("学习启程","开启属于你的学习之旅");
window.day3 = () => showAchieve("勤学萌新","连续3天准时打卡学习");
window.day7 = () => showAchieve("自律达人","连续一周坚持学习不断档");
window.day15 = () => showAchieve("学习先锋","连续半月稳定学习超厉害");
window.day30 = () => showAchieve("月度学霸","整整三十天从未间断");
window.day60 = () => showAchieve("深耕学者","两个月持之以恒坚持学习");
window.day90 = () => showAchieve("学识积淀","百日坚持，厚积薄发");
window.word50 = () => showAchieve("单词入门","累计熟记50个实用单词");
window.word100 = () => showAchieve("百词小能手","成功攻克100个单词大关");
window.word200 = () => showAchieve("词汇新星","累计背诵单词突破200");
window.word500 = () => showAchieve("词汇达人","掌握五百单词，词汇量起飞");
window.word1000 = () => showAchieve("单词宗师","千词在手，外语无忧");
window.word2000 = () => showAchieve("词汇大神","两千词汇储备，从容应对");
window.task10 = () => showAchieve("小有收获","累计完成10项学习任务");
window.task30 = () => showAchieve("稳步前行","累计完成30项学习任务");
window.task50 = () => showAchieve("实干学习者","累计完成50项学习任务");
window.task100 = () => showAchieve("任务达人","圆满完成百项学习任务");
window.task200 = () => showAchieve("学习劳模","累计完成200项学习任务");
window.earlyBird = () => showAchieve("早起学霸","清晨早起优先完成学习任务");
window.nightScholar = () => showAchieve("深夜勤学","深夜依旧坚持充实自己");
window.fullAttendance = () => showAchieve("全勤学子","当月学习打卡全勤无缺席");
window.perfectScore = () => showAchieve("满分学霸","单次任务取得满分佳绩");
window.noMistake = () => showAchieve("细心达人","全程作答零失误");
window.sprintLearner = () => showAchieve("高效速学","快速高质量完成学习内容");

refreshOnAppStart();
