<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'

import { orderApi } from '../api'
import { dateTime, money } from '../utils/format'

const POLL_INTERVAL_MS = 2000
const POLL_LIMIT_MS = 30000

const order = ref(null)
const state = ref('checking') // checking | paid | processing | timeout | error
const error = ref('')
let timer = null

function settle(result) {
  order.value = result
  if (result.status === 'PENDING_PAYMENT') return false
  state.value = result.status === 'CLOSED' ? 'error' : 'paid'
  if (result.status === 'CLOSED') error.value = 'This order has been closed.'
  return true
}

function poll(orderId, startedAt) {
  timer = setTimeout(async () => {
    try {
      if (settle(await orderApi.get(orderId))) return
    } catch {
      // Keep polling until the time limit.
    }
    if (Date.now() - startedAt >= POLL_LIMIT_MS) {
      state.value = 'timeout'
    } else {
      poll(orderId, startedAt)
    }
  }, POLL_INTERVAL_MS)
}

onMounted(async () => {
  // Pass exactly what Alipay appended to return_url; the backend verifies the signature
  // and then trusts only its own query to Alipay.
  const params = Object.fromEntries(new URLSearchParams(window.location.search))
  try {
    const result = await orderApi.returnCheck(params)
    if (!settle(result)) {
      state.value = 'processing'
      poll(result.id, Date.now())
    }
  } catch (e) {
    state.value = 'error'
    error.value = e.message
  }
})

onBeforeUnmount(() => clearTimeout(timer))
</script>

<template>
  <section class="card result">
    <template v-if="state === 'checking'">
      <h1>Checking your payment...</h1>
    </template>

    <template v-else-if="state === 'paid'">
      <div class="result__icon result__icon--ok" aria-hidden="true">&#10003;</div>
      <h1>Payment Successful</h1>
      <p class="muted">Thank you for your order.</p>
    </template>

    <template v-else-if="state === 'processing'">
      <div class="spinner" aria-hidden="true" />
      <h1>Processing</h1>
      <p class="muted">Waiting for Alipay to confirm your payment...</p>
    </template>

    <template v-else-if="state === 'timeout'">
      <h1>Payment Not Confirmed Yet</h1>
      <p class="muted">Alipay has not confirmed the payment yet. Please check My Orders again in a minute.</p>
    </template>

    <template v-else>
      <h1>Something Went Wrong</h1>
      <p class="form-error">{{ error }}</p>
    </template>

    <dl v-if="order && state !== 'error'" class="result__summary">
      <dt>Order</dt><dd>{{ order.orderNo }}</dd>
      <dt>Amount</dt><dd>{{ money(order.totalAmount) }}</dd>
      <dt v-if="order.paidAt">Paid at</dt><dd v-if="order.paidAt">{{ dateTime(order.paidAt) }}</dd>
      <dt>Items</dt>
      <dd>
        <div v-for="item in order.items" :key="item.bookId">{{ item.title }} &times; {{ item.quantity }}</div>
      </dd>
    </dl>

    <div class="actions actions--center">
      <RouterLink to="/orders" class="button button--primary">View My Orders</RouterLink>
      <RouterLink to="/" class="button">Continue Shopping</RouterLink>
    </div>
  </section>
</template>
