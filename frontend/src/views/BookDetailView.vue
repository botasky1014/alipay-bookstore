<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import { bookApi, orderApi } from '../api'
import { useCartStore } from '../stores/cart'
import { useSessionStore } from '../stores/session'
import { useToastStore } from '../stores/toast'
import { money } from '../utils/format'

const route = useRoute()
const session = useSessionStore()
const cart = useCartStore()
const toast = useToastStore()

const book = ref(null)
const quantity = ref(1)
const error = ref('')
const busy = ref(false)

const soldOut = computed(() => book.value?.stock === 0)

async function load() {
  try {
    book.value = await bookApi.get(route.params.id)
    quantity.value = Math.min(Math.max(1, quantity.value), Math.max(1, book.value.stock))
  } catch (e) {
    error.value = e.message
  }
}

onMounted(load)

function validQuantity() {
  const value = Math.floor(Number(quantity.value))
  if (!value || value < 1 || value > book.value.stock) {
    toast.error(`Quantity must be between 1 and ${book.value.stock}`)
    return null
  }
  return value
}

async function addToCart() {
  const value = validQuantity()
  if (!value) return
  busy.value = true
  try {
    await cart.add(book.value.id, value)
    toast.success(`Added "${book.value.title}" to your cart`)
  } catch (e) {
    toast.error(e.message)
  } finally {
    busy.value = false
  }
}

async function buyNow() {
  const value = validQuantity()
  if (!value) return
  busy.value = true
  try {
    const order = await orderApi.create(session.user.id, [{ bookId: book.value.id, quantity: value }], false)
    await load()
    await payOrder(order.id)
  } catch (e) {
    toast.error(e.message)
    await load()
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <RouterLink to="/" class="back-link">&larr; Back to books</RouterLink>
  <p v-if="error" class="form-error">{{ error }}</p>
  <section v-if="book" class="detail">
    <img :src="book.coverImage" :alt="book.title" class="detail__cover" />
    <div class="detail__info">
      <h1>{{ book.title }}</h1>
      <p class="muted">by {{ book.author }}</p>
      <p class="detail__price">{{ money(book.price) }}</p>
      <p :class="['stock', { 'stock--out': soldOut }]">{{ soldOut ? 'Out of stock' : `Stock: ${book.stock}` }}</p>
      <p class="detail__description">{{ book.description }}</p>

      <div class="detail__buy">
        <label class="field field--inline">
          <span>Quantity</span>
          <input v-model.number="quantity" type="number" min="1" :max="book.stock" class="qty-input"
            :disabled="soldOut || busy" />
        </label>
        <button type="button" class="button" :disabled="soldOut || busy" @click="addToCart">Add to Cart</button>
        <button type="button" class="button button--primary" :disabled="soldOut || busy" @click="buyNow">
          Buy Now
        </button>
      </div>
    </div>
  </section>
</template>
