import { createPinia } from "pinia";
import { createApp } from "vue";
import "vant/lib/index.css";
import App from "./App.vue";
import router from "./router";
import "./styles.css";
import { installTracking } from "./tracking";
import { ensureDeviceId } from "./utils/device-id";

ensureDeviceId();

const app = createApp(App);
app.use(createPinia());
app.use(router);
installTracking(router);
app.mount("#app");
