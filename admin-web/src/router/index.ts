import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../stores/user'
import Login from '../views/login/Login.vue'
import InstanceList from '../views/instance/InstanceList.vue'
import TaskEdit from '../views/task/TaskEdit.vue'
import TaskList from '../views/task/TaskList.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/tasks' },
    { path: '/login', component: Login },
    { path: '/tasks', component: TaskList, meta: { requiresAuth: true } },
    { path: '/tasks/new', component: TaskEdit, meta: { requiresAuth: true } },
    { path: '/tasks/:id', component: TaskEdit, meta: { requiresAuth: true } },
    { path: '/instances', component: InstanceList, meta: { requiresAuth: true } },
  ],
})

router.beforeEach((to, _from, next) => {
  const user = useUserStore()
  if (to.meta.requiresAuth && !user.isAuthenticated) {
    next('/login')
  } else if (to.path === '/login' && user.isAuthenticated) {
    next('/tasks')
  } else {
    next()
  }
})
