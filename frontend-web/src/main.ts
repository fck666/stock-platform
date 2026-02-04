import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import './style.css'
import App from './App.vue'
import router from './router'
import { initTheme } from './theme'
import { auth } from './auth/auth'

initTheme()

async function bootstrap() {
  await auth.init()
  createApp(App).use(router).use(ElementPlus).mount('#app')
}

bootstrap()
