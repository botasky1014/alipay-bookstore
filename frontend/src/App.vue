<script setup>
import AppHeader from './components/AppHeader.vue'
import CartPanel from './components/CartPanel.vue'
import PayDialog from './components/PayDialog.vue'
import ToastList from './components/ToastList.vue'
import { useSessionStore } from './stores/session'

const session = useSessionStore()
</script>

<template>
  <AppHeader v-if="session.user" />
  <main class="container">
    <RouterView />
  </main>
  <template v-if="session.user && !session.isAdmin">
    <CartPanel />
    <PayDialog />
  </template>
  <ToastList />
</template>
