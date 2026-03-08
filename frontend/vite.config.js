import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  return {
    plugins: [vue()],
    base: './', // 保持这个，对移动端很重要
    resolve: {
      alias: {
        '@': resolve(__dirname, 'src')
      }
    },
    server: {
      port: 3000,
      proxy: {
        '/api': {
          target: env.VITE_API_PROXY_TARGET || 'http://localhost:8080',
          changeOrigin: true
        },
        '/tutor': {
          target: env.VITE_TUTOR_API_PROXY_TARGET || 'http://127.0.0.1:8000',
          changeOrigin: true
        }
      },
      host: true, // 允许外部访问，方便真机调试
      // 添加热更新配置
      hmr: {
        overlay: true
      }
    },
    build: {
      outDir: 'dist',
      assetsDir: 'assets',
      sourcemap: false, // 生产环境关闭 sourcemap 减小体积

      // 新增移动端优化配置
      chunkSizeWarningLimit: 1500, // 提高块大小警告限制
      rollupOptions: {
        output: {
          // 代码分割优化
          manualChunks: {
            'vue-vendor': ['vue', 'vue-router', 'pinia'],
            'element-ui': ['element-plus', '@element-plus/icons-vue'],
            'axios-utils': ['axios']
          },
          // 文件命名优化
          chunkFileNames: 'js/[name]-[hash].js',
          entryFileNames: 'js/[name]-[hash].js',
          assetFileNames: (assetInfo) => {
            const info = assetInfo.name.split('.')
            const ext = info[info.length - 1]
            if (/\.(mp4|webm|ogg|mp3|wav|flac|aac)(\?.*)?$/i.test(assetInfo.name)) {
              return `media/[name]-[hash].[ext]`
            }
            if (/\.(png|jpe?g|gif|svg|webp|avif)(\?.*)?$/i.test(assetInfo.name)) {
              return `img/[name]-[hash].[ext]`
            }
            if (/\.(woff2?|eot|ttf|otf)(\?.*)?$/i.test(assetInfo.name)) {
              return `fonts/[name]-[hash].[ext]`
            }
            return `assets/[name]-[hash].[ext]`
          }
        }
      },

      // 构建优化
      minify: 'terser',
      terserOptions: {
        compress: {
          drop_console: true, // 移除 console
          drop_debugger: true // 移除 debugger
        }
      }
    },

    // 新增预览服务器配置
    preview: {
      port: 3000,
      host: true
    },

    // 环境变量配置
    define: {
      // 定义全局常量
      __APP_VERSION__: JSON.stringify(process.env.npm_package_version)
    },

    // CSS 配置优化
    css: {
      preprocessorOptions: {
        scss: {
          // variables.scss 文件不存在，移除此引用以避免构建错误
        }
      }
    }
  }
})