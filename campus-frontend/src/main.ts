import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

// ---- 样式加载顺序：Element Plus 基础样式 → 项目设计令牌 → 基础层 → 主题映射 ----
import 'element-plus/dist/index.css'
import './styles/tokens.css'
import './styles/base.css'
import './styles/element-plus.css'

import App from './App.vue'
import router from './router'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: undefined })

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.mount('#app')
