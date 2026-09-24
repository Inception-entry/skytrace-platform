import { createRouter, createWebHistory } from 'vue-router'
import { authenticationState } from '@/auth/keycloak'

const routes = [
  {
    path: '/',
    redirect: '/drone',
  },
  {
    path: '/map',
    component: () => import('../views/Home.vue'),
  },
  {
    path: '/drone',
    name: 'drone',
    component: () => import('../views/DroneView.vue'),
  },
  {
    path: '/devices',
    name: 'devices',
    component: () => import('../views/DeviceView.vue'),
  },
  {
    path: '/routes',
    name: 'routes',
    component: () => import('../views/RouteView.vue'),
  },
  {
    path: '/chat',
    name: 'chat',
    component: () => import('../views/ChatView.vue'),
    meta: {
      roles: ['ADMIN', 'OPERATOR'],
    },
  },
  {
    path: '/knowledge',
    name: 'knowledge',
    component: () => import('../views/KnowledgeView.vue'),
  },
  {
    path: '/audit',
    name: 'audit',
    component: () => import('../views/AdminView.vue'),
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
    component: () => import('../views/EvidenceView.vue'),
  },
  {
    path: '/401',
    name: 'unauthorized',
    component: () => import('../views/AuthorizationErrorView.vue'),
    props: { status: 401 }
  },
  {
    path: '/403',
    name: 'forbidden',
    component: () => import('../views/AuthorizationErrorView.vue'),
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
