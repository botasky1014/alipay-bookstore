<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import { adminApi, bookApi } from '../../api'
import { useToastStore } from '../../stores/toast'
import { money } from '../../utils/format'

const route = useRoute()
const toast = useToastStore()

const book = ref(null)
const stockDelta = ref(0)
const price = ref('')
const title = ref('')
const error = ref('')
const busy = ref(false)

function fill(value) {
  book.value = value
  stockDelta.value = 0
  price.value = Number(value.price).toFixed(2)
  title.value = value.title
}

onMounted(async () => {
  try {
    fill(await bookApi.get(route.params.id))
  } catch (e) {
    error.value = e.message
  }
})

async function save() {
  error.value = ''
  const changes = {}
  const delta = Number(stockDelta.value)
  if (!Number.isInteger(delta)) {
    error.value = 'Stock change must be a whole number'
    return
  }
  if (delta !== 0) changes.stockDelta = delta
  if (!/^\d+(\.\d{1,2})?$/.test(String(price.value).trim()) || Number(price.value) <= 0) {
    error.value = 'Price must be greater than 0 with at most 2 decimal places'
    return
  }
  if (Number(price.value) !== Number(book.value.price)) changes.price = String(price.value).trim()
  if (!title.value.trim()) {
    error.value = 'Title must not be empty'
    return
  }
  if (title.value.trim() !== book.value.title) changes.title = title.value.trim()
  if (!Object.keys(changes).length) {
    toast.info('Nothing to save')
    return
  }

  busy.value = true
  try {
    fill(await adminApi.updateBook(book.value.id, changes))
    toast.success('Book updated')
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <RouterLink to="/admin/inventory" class="back-link">&larr; Back to inventory</RouterLink>
  <section v-if="book" class="detail">
    <img :src="book.coverImage" :alt="book.title" class="detail__cover" />
    <form class="detail__info" @submit.prevent="save">
      <h1>{{ book.title }}</h1>
      <p class="muted">by {{ book.author }}</p>
      <p>Current price <strong>{{ money(book.price) }}</strong> &middot; Current stock <strong>{{ book.stock }}</strong></p>

      <label class="field">
        <span>Stock change (+ to add, - to remove)</span>
        <input v-model.number="stockDelta" type="number" step="1" />
        <small class="muted">New stock will be {{ book.stock + (Number(stockDelta) || 0) }}. Stock cannot go below 0.</small>
      </label>
      <label class="field">
        <span>Price (CNY)</span>
        <input v-model="price" type="text" inputmode="decimal" />
      </label>
      <label class="field">
        <span>Title</span>
        <input v-model="title" type="text" maxlength="255" />
      </label>
      <p class="muted">Price and title changes apply to new orders only.</p>

      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <button type="submit" class="button button--primary" :disabled="busy">Save Changes</button>
    </form>
  </section>
  <p v-else-if="error" class="form-error">{{ error }}</p>
</template>
