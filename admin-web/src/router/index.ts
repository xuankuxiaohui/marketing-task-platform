import { createRouter, createWebHistory, type RouteLocationNormalized } from 'vue-router'
import { useUserStore } from '../stores/user'
import { useTabStore } from '../stores/tab'
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
import AdminUserList from '../views/user/AdminUserList.vue'
import ClientUserList from '../views/user/ClientUserList.vue'
import SignInConfigList from '../views/signin/SignInConfigList.vue'
import SignInConfigEdit from '../views/signin/SignInConfigEdit.vue'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    title?: string | ((route: RouteLocationNormalized) => string)
    noTab?: boolean
  }
}

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/tasks', meta: { noTab: true } },
    { path: '/login', component: Login, meta: { noTab: true } },
    { path: '/tasks', name: 'TaskList', component: TaskList, meta: { requiresAuth: true, title: '任务列表' } },
    { path: '/tasks/new', name: 'TaskNew', component: TaskEdit, meta: { requiresAuth: true, title: '新建任务' } },
    { path: '/tasks/:id', name: 'TaskEdit', component: TaskEdit, meta: { requiresAuth: true, title: (route) => `编辑任务 #${route.params.id}` } },
    { path: '/instances', name: 'InstanceList', component: InstanceList, meta: { requiresAuth: true, title: '实例查询' } },
    { path: '/instances/:id', name: 'InstanceDetail', component: InstanceDetail, meta: { requiresAuth: true, title: (route) => `实例详情 #${route.params.id}` } },
    { path: '/mutex-groups', name: 'MutexGroupList', component: MutexGroupList, meta: { requiresAuth: true, title: '互斥组管理' } },
    { path: '/mutex-groups/:id', name: 'MutexGroupDetail', component: MutexGroupDetail, meta: { requiresAuth: true, title: (route) => `互斥组详情 #${route.params.id}` } },
    { path: '/prizes', name: 'PrizeList', component: PrizeList, meta: { requiresAuth: true, title: '奖品配置' } },
    { path: '/prizes/new', name: 'PrizeNew', component: PrizeEdit, meta: { requiresAuth: true, title: '新建奖品' } },
    { path: '/prizes/:id', name: 'PrizeEdit', component: PrizeEdit, meta: { requiresAuth: true, title: (route) => `编辑奖品 #${route.params.id}` } },
    { path: '/prize-records', name: 'PrizeRecordList', component: PrizeRecordList, meta: { requiresAuth: true, title: '奖品记录' } },
    { path: '/operation-logs', name: 'OperationLogs', component: OperationLogs, meta: { requiresAuth: true, title: '操作日志' } },
    {
      path: '/dashboard',
      name: 'Dashboard',
      component: () => import('../views/Dashboard.vue'),
      meta: { requiresAuth: true, title: '运营仪表盘' }
    },
    { path: '/admin-users', name: 'AdminUserList', component: AdminUserList, meta: { requiresAuth: true, title: '后台用户管理' } },
    { path: '/client-users', name: 'ClientUserList', component: ClientUserList, meta: { requiresAuth: true, title: '客户端用户管理' } },
    { path: '/signin-configs', name: 'SignInConfigList', component: SignInConfigList, meta: { requiresAuth: true, title: '签到活动' } },
    { path: '/signin-configs/new', name: 'SignInConfigNew', component: SignInConfigEdit, meta: { requiresAuth: true, title: '新建签到活动' } },
    { path: '/signin-configs/:id', name: 'SignInConfigEdit', component: SignInConfigEdit, meta: { requiresAuth: true, title: (route) => `编辑签到活动 #${route.params.id}` } },
    { path: '/simulate', name: 'SimulatePage', component: SimulatePage, meta: { requiresAuth: true, title: '模拟测试' } },
    {
      path: '/tasks/:id/metrics',
      name: 'TaskMetrics',
      component: () => import('../views/TaskMetrics.vue'),
      meta: { requiresAuth: true, title: (route) => `任务指标 #${route.params.id}` }
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

router.afterEach((to) => {
  const tabStore = useTabStore()
  tabStore.addTab(to)
})
