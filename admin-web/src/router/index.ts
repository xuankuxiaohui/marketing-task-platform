import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../stores/user'
import Login from '../views/login/Login.vue'
import InstanceList from '../views/instance/InstanceList.vue'
import PrizeEdit from '../views/prize/PrizeEdit.vue'
import PrizeList from '../views/prize/PrizeList.vue'
import PrizeRecordList from '../views/prize/PrizeRecordList.vue'
import TaskEdit from '../views/task/TaskEdit.vue'
import TaskList from '../views/task/TaskList.vue'
import MutexGroupList from '../views/mutex-group/MutexGroupList.vue'
import MutexGroupDetail from '../views/mutex-group/MutexGroupDetail.vue'
import SimulatePage from '../views/simulate/SimulatePage.vue'
import InstanceDetail from '../views/instance/InstanceDetail.vue'
import OperationLogs from '../views/OperationLogs.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/tasks' },
    { path: '/login', component: Login },
    { path: '/tasks', component: TaskList, meta: { requiresAuth: true } },
    { path: '/tasks/new', component: TaskEdit, meta: { requiresAuth: true } },
    { path: '/tasks/:id', component: TaskEdit, meta: { requiresAuth: true } },
    { path: '/instances', component: InstanceList, meta: { requiresAuth: true } },
    { path: '/instances/:id', component: InstanceDetail, meta: { requiresAuth: true } },
    { path: '/mutex-groups', component: MutexGroupList, meta: { requiresAuth: true } },
    { path: '/mutex-groups/:id', component: MutexGroupDetail, meta: { requiresAuth: true } },
    { path: '/prizes', component: PrizeList, meta: { requiresAuth: true } },
    { path: '/prizes/new', component: PrizeEdit, meta: { requiresAuth: true } },
    { path: '/prizes/:id', component: PrizeEdit, meta: { requiresAuth: true } },
    { path: '/prize-records', component: PrizeRecordList, meta: { requiresAuth: true } },
    { path: '/operation-logs', component: OperationLogs, meta: { requiresAuth: true } },
    {
      path: '/dashboard',
      name: 'Dashboard',
      component: () => import('../views/Dashboard.vue'),
      meta: { requiresAuth: true, title: '运营仪表盘' }
    },
    { path: '/simulate', component: SimulatePage, meta: { requiresAuth: true } },
    {
      path: '/tasks/:id/metrics',
      name: 'TaskMetrics',
      component: () => import('../views/TaskMetrics.vue'),
      meta: { requiresAuth: true, title: '任务指标' }
    },
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
