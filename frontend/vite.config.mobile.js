import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

// 移动端专用配置
export default defineConfig({
    plugins: [vue()],
    base: './',
    resolve: {
        alias: {
            '@': resolve(__dirname, 'src')
        }
    },
    server: {
        port: 3000,
        host: '0.0.0.0', // 明确指定 0.0.0.0 确保外部访问
        proxy: {
            '/api': {
                target: 'http://localhost:8080',
                changeOrigin: true
            }
        }
    },
    build: {
        outDir: 'dist',
        assetsDir: 'assets',
        sourcemap: false,

        // 移动端专用优化
        rollupOptions: {
            output: {
                manualChunks: {
                    'vue-core': ['vue'],
                    'router-store': ['vue-router', 'pinia'],
                    'ui-library': ['element-plus', '@element-plus/icons-vue'],
                    'http-library': ['axios']
                }
            }
        },

        // 更激进的压缩
        minify: 'terser',
        terserOptions: {
            compress: {
                drop_console: true,
                drop_debugger: true,
                pure_funcs: ['console.log'] // 移除所有 console.log
            }
        }
    },

    // 移动端特定配置
    define: {
        __IS_MOBILE_BUILD__: true, // 标识这是移动端构建
        __APP_VERSION__: JSON.stringify(process.env.npm_package_version)
    }
})