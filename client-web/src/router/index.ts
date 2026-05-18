import { createRouter, createWebHistory } from 'vue-router'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/tasks' },
    { path: '/login', component: () => import('../views/login/MockLogin.vue') },
    { path: '/tasks', component: () => import('../views/task/TaskList.vue') },
    { path: '/task/:id', component: () => import('../views/task/TaskDetail.vue') },
  ],
})
