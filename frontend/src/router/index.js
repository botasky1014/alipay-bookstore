import { createRouter, createWebHistory } from 'vue-router'

import { useSessionStore } from '../stores/session'

const routes = [
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { public: true } },

  // Buyer pages
  { path: '/', name: 'home', component: () => import('../views/HomeView.vue'), meta: { role: 'USER' } },
  { path: '/books/:id', name: 'book', component: () => import('../views/BookDetailView.vue'), meta: { role: 'USER' } },
  { path: '/orders', name: 'orders', component: () => import('../views/OrdersView.vue'), meta: { role: 'USER' } },
  // Alipay redirects the browser here after payment. It must work even when this browser has no
  // stored login, otherwise the redirect to /login would drop Alipay's return parameters.
  {
    path: '/pay/return',
    name: 'pay-return',
    component: () => import('../views/PayReturnView.vue'),
    meta: { anyone: true },
  },

  // Admin pages
  { path: '/admin', redirect: '/admin/inventory' },
  {
    path: '/admin/inventory',
    name: 'admin-inventory',
    component: () => import('../views/admin/InventoryView.vue'),
    meta: { role: 'ADMIN' },
  },
  {
    path: '/admin/books/:id',
    name: 'admin-book',
    component: () => import('../views/admin/BookEditView.vue'),
    meta: { role: 'ADMIN' },
  },
  {
    path: '/admin/refunds',
    name: 'admin-refunds',
    component: () => import('../views/admin/RefundsView.vue'),
    meta: { role: 'ADMIN' },
  },
  { path: '/admin/funds', name: 'admin-funds', component: () => import('../views/admin/FundsView.vue'), meta: { role: 'ADMIN' } },
  {
    path: '/admin/settings',
    name: 'admin-settings',
    component: () => import('../views/admin/SettingsView.vue'),
    meta: { role: 'ADMIN' },
  },

  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

// Navigation convenience only, not access control (design section 8).
router.beforeEach((to) => {
  const session = useSessionStore()
  if (to.meta.anyone) {
    return true
  }
  if (to.meta.public) {
    return session.user ? session.homePath : true
  }
  if (!session.user) {
    // Come back to the requested page after logging in.
    return { path: '/login', query: to.fullPath === '/' ? {} : { redirect: to.fullPath } }
  }
  if (to.meta.role && to.meta.role !== session.user.role) {
    return session.homePath
  }
  return true
})

export default router
