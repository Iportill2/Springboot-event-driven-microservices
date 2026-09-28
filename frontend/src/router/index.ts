import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import LoginView from '../views/LoginView.vue'
import RegisterView from '../views/RegisterView.vue'
import VerifyView from '../views/VerifyView.vue'
import { getToken } from '../api/client'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', name: 'home', component: HomeView },
    { path: '/login', name: 'login', component: LoginView },
    { path: '/register', name: 'register', component: RegisterView },
    { path: '/verify', name: 'verify', component: VerifyView },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

const AUTH_PAGES = ['login', 'register', 'verify']

router.beforeEach((to) => {
  const authed = !!getToken()
  if (!AUTH_PAGES.includes(to.name as string) && !authed) return { name: 'login' }
  if (AUTH_PAGES.includes(to.name as string) && authed) return { name: 'home' }
})

export default router