import { createRouter, createWebHistory } from 'vue-router'
import MockLogin from '../views/login/MockLogin.vue'
import InstanceList from '../views/instance/InstanceList.vue'
import TaskEdit from '../views/task/TaskEdit.vue'
import TaskList from '../views/task/TaskList.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/tasks' },
    { path: '/login', component: MockLogin },
    { path: '/tasks', component: TaskList },
    { path: '/tasks/new', component: TaskEdit },
    { path: '/tasks/:id', component: TaskEdit },
    { path: '/instances', component: InstanceList },
  ],
})
