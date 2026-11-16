<script setup>
import { useRouter } from 'vue-router'

import { useCartStore } from '../stores/cart'
import { useSessionStore } from '../stores/session'

const session = useSessionStore()
const cart = useCartStore()
const router = useRouter()

if (!session.isAdmin) {
  cart.load().catch(() => {})
}

function logout() {
  cart.reset()
  session.logout()
  router.push('/login')
}
</script>

<template>
  <header class="app-header">
    <div class="app-header__inner">
      <span class="brand">Alipay Bookstore</span>
      <span class="user-chip" data-testid="current-user">{{ session.user.username }}</span>
      <nav class="nav">
        <template v-if="session.isAdmin">
          <RouterLink to="/admin/inventory">Inventory</RouterLink>
          <RouterLink to="/admin/refunds">Refund Requests</RouterLink>
          <RouterLink to="/admin/funds">Funds</RouterLink>
          <RouterLink to="/admin/settings">Settings</RouterLink>
        </template>
        <template v-else>
          <RouterLink to="/" exact-active-class="router-link-active">Home</RouterLink>
          <RouterLink to="/orders">My Orders</RouterLink>
          <button type="button" class="nav-button" @click="cart.show()">
            Cart<span v-if="cart.count" class="badge">{{ cart.count }}</span>
          </button>
        </template>
        <button type="button" class="nav-button" @click="logout">Logout</button>
      </nav>
    </div>
  </header>
</template>
