import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import { cartApi } from '../api'
import { useSessionStore } from './session'

/** Server-side cart plus the side panel's open state and selection. */
export const useCartStore = defineStore('cart', () => {
  const items = ref([])
  const open = ref(false)
  const loading = ref(false)
  const selected = ref(new Set())

  const count = computed(() => items.value.reduce((sum, item) => sum + item.quantity, 0))
  const selectedItems = computed(() => items.value.filter((item) => selected.value.has(item.id)))
  const selectedTotal = computed(() =>
    selectedItems.value.reduce((sum, item) => sum + Number(item.subtotal), 0),
  )

  function userId() {
    return useSessionStore().user?.id
  }

  function apply(cart) {
    items.value = cart.items
    // Keep the selection only for items that still exist.
    const ids = new Set(cart.items.map((item) => item.id))
    selected.value = new Set([...selected.value].filter((id) => ids.has(id)))
  }

  async function load() {
    if (!userId()) return
    loading.value = true
    try {
      apply(await cartApi.get(userId()))
    } finally {
      loading.value = false
    }
  }

  async function add(bookId, quantity) {
    apply(await cartApi.add(userId(), bookId, quantity))
  }

  async function update(itemId, quantity) {
    apply(await cartApi.update(itemId, quantity))
  }

  async function remove(itemId) {
    apply(await cartApi.remove(itemId))
  }

  function toggle(itemId) {
    const next = new Set(selected.value)
    if (next.has(itemId)) next.delete(itemId)
    else next.add(itemId)
    selected.value = next
  }

  function selectAll(value) {
    selected.value = value ? new Set(items.value.map((item) => item.id)) : new Set()
  }

  function show() {
    open.value = true
    load()
  }

  function reset() {
    items.value = []
    selected.value = new Set()
    open.value = false
  }

  return {
    items,
    open,
    loading,
    selected,
    count,
    selectedItems,
    selectedTotal,
    load,
    add,
    update,
    remove,
    toggle,
    selectAll,
    show,
    reset,
  }
})
