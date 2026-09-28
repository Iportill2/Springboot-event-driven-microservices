import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '../layouts/AppLayout.vue'
import HomeView from '../views/HomeView.vue'
import LoginView from '../views/LoginView.vue'
import RegisterView from '../views/RegisterView.vue'
import VerifyView from '../views/VerifyView.vue'
import ProfileView from '../views/ProfileView.vue'
import AdminProductsView from '../views/admin/ProductsView.vue'
import AdminUsersView from '../views/admin/UsersView.vue'
import { authenticated, hasAnyRole } from '../composables/useAuth'

declare module 'vue-router' {
  interface RouteMeta {
    /** Ruta privada: redirige a login conservando el destino. */
    requiresAuth?: boolean
    /** Ruta de invitado: un usuario autenticado vuelve al inicio. */
    guestOnly?: boolean
    /** Roles con acceso. Si el usuario no los tiene, vuelve al inicio. */
    roles?: string[]
  }
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { guestOnly: true } },
    { path: '/register', name: 'register', component: RegisterView, meta: { guestOnly: true } },
    { path: '/verify', name: 'verify', component: VerifyView },
    {
      // Ruta padre: agrupa las vistas que comparten el shell con navegacion.
      // El catalogo cuelga de aqui pero es publico, asi que no lleva requiresAuth.
      path: '/',
      component: AppLayout,
      children: [
        { path: '', name: 'home', component: HomeView },
        { path: 'profile', name: 'profile', component: ProfileView, meta: { requiresAuth: true } },
        {
          path: 'admin/products',
          name: 'admin-products',
          component: AdminProductsView,
          meta: { requiresAuth: true, roles: ['ADMIN'] },
        },
        {
          path: 'admin/users',
          name: 'admin-users',
          component: AdminUsersView,
          meta: { requiresAuth: true, roles: ['ADMIN'] },
        },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach((to) => {
  if (to.meta.requiresAuth && !authenticated.value) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.roles && !hasAnyRole(to.meta.roles)) {
    return { name: 'home' }
  }
  if (to.meta.guestOnly && authenticated.value) {
    return { name: 'home' }
  }
})

export default router
