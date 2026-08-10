import { createRouter, createWebHistory } from 'vue-router'
import Home from '../views/Home.vue'
import DroneView from '../views/DroneView.vue'
import DeviceView from '../views/DeviceView.vue'
import RouteView from '../views/RouteView.vue'
import ChatView from '../views/ChatView.vue'
import KnowledgeView from '../views/KnowledgeView.vue'
import AdminView from '../views/AdminView.vue'
import AuthorizationErrorView from '../views/AuthorizationErrorView.vue'
import EvidenceView from '../views/EvidenceView.vue'
import { authenticationState } from '@/auth/keycloak'

const routes = [
  {
    path: '/',
    redirect: '/drone',
  },
  {
    path: '/map',
    component: Home,
  },
  {
    path: '/drone',
    name: 'drone',
    component: DroneView,
  },
  {
    path: '/devices',
    name: 'devices',
    component: DeviceView,
  },
  {
    path: '/routes',
    name: 'routes',
    component: RouteView,
  },
  {
    path: '/chat',
    name: 'chat',
    component: ChatView,
    meta: {
      roles: ['ADMIN', 'OPERATOR'],
    },
  },
  {
    path: '/knowledge',
    name: 'knowledge',
    component: KnowledgeView,
  },
  {
    path: '/audit',
    name: 'audit',
    component: AdminView,
    meta: {
      roles: ['ADMIN'],
    },
  },
  {
    path: '/admin',
    redirect: '/audit',
  },
  {
    path: '/evidence',
    name: 'evidence',
    component: EvidenceView,
  },
  {
    path: '/401',
    name: 'unauthorized',
    component: AuthorizationErrorView,
    props: { status: 401 }
  },
  {
    path: '/403',
    name: 'forbidden',
    component: AuthorizationErrorView,
    props: { status: 403 }
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  if (to.path === '/401' || to.path === '/403') {
    return true
  }

  const requiredRoles = to.meta.roles as string[] | undefined
  if (!requiredRoles?.length) {
    return true
  }

  const permitted = requiredRoles.some((role) =>
    authenticationState.roles.includes(role),
  )
  if (permitted) {
    return true
  }

  return {
    path: '/403',
    query: {
      redirect: '/drone',
      requested: to.fullPath,
    },
  }
})

export default router
