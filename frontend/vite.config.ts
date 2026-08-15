import path from "path"
import react from "@vitejs/plugin-react"
import { defineConfig } from "vite"

// https://vite.dev/config/
export default defineConfig({
  base: './',
  plugins: [react()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  server: {
    host: true, // 监听所有网卡(含 0.0.0.0 与 IPv4)，避免 localhost/IPv6 解析导致无法访问
    port: 5173,
    strictPort: true,
    proxy: {
      // 开发环境将 /api 代理到后端，避免跨域（含 /api/book/* 与 /api/crawl/*）
      "/api": {
        target: process.env.VITE_API_TARGET || "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
});
