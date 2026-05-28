import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import LoginView from '../views/LoginView.vue'
import RegisterView from '../views/RegisterView.vue'
import DashboardView from '../views/DashboardView.vue'
import SchedulesView from '../views/SchedulesView.vue'
import ReportsView from '../views/ReportsView.vue'
import FilesView from '../views/FilesView.vue'
import AdminView from '../views/AdminView.vue'

const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/login', component: LoginView },
  { path: '/register', component: RegisterView },
  { path: '/dashboard', component: DashboardView, meta: { requiresAuth: true } },
  { path: '/schedules', component: SchedulesView, meta: { requiresAuth: true } },
  { path: '/reports', component: ReportsView, meta: { requiresAuth: true } },
  { path: '/files', component: FilesView, meta: { requiresAuth: true } },
  { path: '/admin', component: AdminView, meta: { requiresAuth: true } },
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const authStore = useAuthStore()
  if (to.meta.requiresAuth && !authStore.username) {
    return '/login'
  }
})

export default router
