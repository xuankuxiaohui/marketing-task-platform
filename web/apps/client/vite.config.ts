import { fileURLToPath, URL } from "node:url";
import vue from "@vitejs/plugin-vue";
import { VantResolver } from "@vant/auto-import-resolver";
import AutoImport from "unplugin-auto-import/vite";
import Components from "unplugin-vue-components/vite";
import { defineConfig, loadEnv } from "vite";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const proxyTarget = process.env.PORTAL_PROXY_TARGET || env.PORTAL_PROXY_TARGET || "http://127.0.0.1:8081";
  return {
    plugins: [
      vue(),
      AutoImport({ resolvers: [VantResolver()] }),
      Components({ resolvers: [VantResolver()] }),
    ],
    resolve: {
      alias: {
        "@": fileURLToPath(new URL("./src", import.meta.url)),
      },
    },
    server: {
      port: 5174,
      proxy: {
        "/api": {
          target: proxyTarget,
          changeOrigin: true,
        },
      },
    },
    preview: {
      port: 4174,
      proxy: {
        "/api": {
          target: proxyTarget,
          changeOrigin: true,
        },
      },
    },
    build: {
      sourcemap: false,
    },
  };
});
