<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { authApi } from '../api'
import { useSessionStore } from '../stores/session'

const session = useSessionStore()
const router = useRouter()
const route = useRoute()

/** Only same-site paths for the logged-in role are followed; anything else goes to the home page. */
function targetAfterLogin() {
  const redirect = route.query.redirect
  if (typeof redirect !== 'string' || !redirect.startsWith('/') || redirect.startsWith('//')) {
    return session.homePath
  }
  const isAdminPath = redirect.startsWith('/admin')
  return isAdminPath === session.isAdmin ? redirect : session.homePath
}
const username = ref('')
const error = ref('')
const busy = ref(false)

async function submit() {
  error.value = ''
  if (!username.value.trim()) {
    error.value = 'Please enter a username'
    return
  }
  busy.value = true
  try {
    session.login(await authApi.login(username.value.trim()))
    router.push(targetAfterLogin())
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="login">
    <form class="card login__card" @submit.prevent="submit">
      <h1>Alipay Bookstore</h1>
      <p class="muted">Sign in with your username. No password is needed.</p>
      <label class="field">
        <span>Username</span>
        <input v-model="username" type="text" autocomplete="username" autofocus placeholder="buyer or admin" />
      </label>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <button type="submit" class="button button--primary button--block" :disabled="busy">Log in</button>
    </form>
  </div>
</template>
