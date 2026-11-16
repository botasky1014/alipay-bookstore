import { defineStore } from 'pinia'
import { ref } from 'vue'

let nextId = 1

/** Short notifications shown in the corner of the screen. */
export const useToastStore = defineStore('toast', () => {
  const toasts = ref([])

  function show(message, type = 'info', timeout = 4000) {
    const id = nextId++
    toasts.value.push({ id, message, type })
    setTimeout(() => dismiss(id), timeout)
  }

  function dismiss(id) {
    toasts.value = toasts.value.filter((toast) => toast.id !== id)
  }

  return {
    toasts,
    success: (message) => show(message, 'success'),
    error: (message) => show(message, 'error', 6000),
    info: (message) => show(message, 'info'),
    dismiss,
  }
})
