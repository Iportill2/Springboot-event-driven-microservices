import { createApp } from 'vue'
import './style.css'
import App from './App.vue'
import router from './router'
import { applyBrand } from './brand'

applyBrand()

createApp(App).use(router).mount('#app')