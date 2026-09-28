import { createApp } from 'vue'
import './style.css'
import App from './App.vue'
import router from './router'
import { applyBrand } from './brand'
import { onUnauthorized } from './api/client'
import { logout } from './composables/useAuth'

applyBrand()

// El JWT caduca a la hora. Cuando el backend empieza a devolver 401 hay que
// cerrar sesion y devolver al login conservando la ruta, para que el usuario
// vuelva a donde estaba tras volver a autenticarse.
onUnauthorized(() => {
  const redirect = router.currentRoute.value.fullPath
  logout()
  if (router.currentRoute.value.name !== 'login') {
    void router.push({ name: 'login', query: { redirect } })
  }
})

createApp(App).use(router).mount('#app')
