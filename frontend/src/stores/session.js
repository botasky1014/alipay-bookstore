import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

const STORAGE_KEY = 'bookstore.user'

function readStoredUser() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

/** Logged-in user kept in localStorage. There is no real authentication (design 6.1). */
export const useSessionStore = defineStore('session', () => {
  const user = ref(readStoredUser())

  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  const homePath = computed(() => (isAdmin.value ? '/admin/inventory' : '/'))

  function login(loggedInUser) {
    user.value = loggedInUser
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(loggedInUser))
    } catch {
      // Storage unavailable: the session lasts until the page is reloaded.
    }
  }

  function logout() {
    user.value = null
    try {
      localStorage.removeItem(STORAGE_KEY)
    } catch {
      // Ignore.
    }
  }

  return { user, isAdmin, homePath, login, logout }
})
