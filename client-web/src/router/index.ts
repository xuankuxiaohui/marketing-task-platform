import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../stores/user'
import Login from '../views/login/Login.vue'
import Register from '../views/login/Register.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/tasks' },
    { path: '/login', component: Login },
    { path: '/register', component: Register },
    { path: '/mock-login', component: () => import('../views/login/MockLogin.vue') },
    { path: '/tasks', component: () => import('../views/task/TaskList.vue'), meta: { requiresAuth: true } },
    { path: '/task/:id', component: () => import('../views/task/TaskDetail.vue'), meta: { requiresAuth: true } },
  ],
})

router.beforeEach((to, _from, next) => {
  const user = useUserStore()
  if (to.meta.requiresAuth && !user.isAuthenticated) {
    next('/login')
  } else if ((to.path === '/login' || to.path === '/register') && user.isAuthenticated) {
    next('/tasks')
  } else {
    next()
  }
})
