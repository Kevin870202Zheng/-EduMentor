import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      // 所有 API → 真实后端
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // SSE 端点特殊处理：禁用 Vite 代理缓存
      '/qa/ask/stream': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      }
    }
  },
  resolve: {
    alias: {
      '@': '/src',
    },
  },
  build: {
    cssCodeSplit: true,
    sourcemap: false,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules/antd/') || id.includes('node_modules/@ant-design/') || id.includes('node_modules/react') || id.includes('node_modules/scheduler/') || id.includes('node_modules/react-dom')) {
            return 'vendor-core';
          }
          if (id.includes('node_modules/echarts/')) {
            return 'vendor-echarts';
          }
          if (id.includes('node_modules/axios/')) {
            return 'vendor-axios';
          }
          if (id.includes('node_modules/react-router')) {
            return 'vendor-router';
          }
        },
        chunkFileNames: 'assets/[name]-[hash].js',
        entryFileNames: 'assets/[name]-[hash].js',
        assetFileNames: 'assets/[name]-[hash][extname]',
      },
    },
    chunkSizeWarningLimit: 500,
    minify: 'esbuild',
    target: 'es2015',
  },
});
