import { createApp } from "vue";
import { createPinia } from "pinia";
import ElementPlus from "element-plus";
import zhCn from "element-plus/dist/locale/zh-cn.mjs";
import "element-plus/dist/index.css";
import * as ElementPlusIconsVue from "@element-plus/icons-vue";
import App from "./App.vue";
import router from "./router";
import { clearAuthStorage, getAuthHeader } from "@/utils/auth";
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
    // TODO: 对接真实的错误监控服务（如 Sentry）
    // 示例: fetch('/api/errors', { method: 'POST', body: JSON.stringify(errorInfo) })
    try {
      // 可选：发送到后端错误日志端点
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
