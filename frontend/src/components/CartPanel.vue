<script setup>
import { computed, ref } from 'vue'

import { orderApi } from '../api'
import { useCartStore } from '../stores/cart'
import { useSessionStore } from '../stores/session'
import { useToastStore } from '../stores/toast'
import { payOrder } from '../utils/payment'
import { money } from '../utils/format'

const cart = useCartStore()
const session = useSessionStore()
const toast = useToastStore()
const busy = ref(false)

const allSelected = computed(() => cart.items.length > 0 && cart.selected.size === cart.items.length)

async function run(action) {
  busy.value = true
  try {
    await action()
  } catch (error) {
    toast.error(error.message)
    await cart.load().catch(() => {})
  } finally {
    busy.value = false
  }
}

function changeQuantity(item, event) {
  const quantity = Math.max(1, Math.min(item.stock, Math.floor(Number(event.target.value) || 1)))
  event.target.value = quantity
  if (quantity !== item.quantity) run(() => cart.update(item.id, quantity))
}

function checkout() {
  run(async () => {
    const items = cart.selectedItems.map((item) => ({ bookId: item.bookId, quantity: item.quantity }))
    const order = await orderApi.create(session.user.id, items, true)
    await cart.load()
    cart.open = false
    await payOrder(order.id)
  })
}
</script>

<template>
  <div v-if="cart.open" class="drawer-overlay" @click="cart.open = false" />
  <aside :class="['drawer', { 'drawer--open': cart.open }]" aria-label="Shopping cart">
    <div class="drawer__header">
      <h2>Cart</h2>
      <button type="button" class="icon-button" aria-label="Close cart" @click="cart.open = false">&times;</button>
    </div>

    <div class="drawer__body">
      <p v-if="!cart.items.length" class="muted">{{ cart.loading ? 'Loading...' : 'Your cart is empty.' }}</p>
      <template v-else>
        <label class="select-all">
          <input type="checkbox" :checked="allSelected" @change="cart.selectAll($event.target.checked)" />
          Select all
        </label>
        <ul class="cart-list">
          <li v-for="item in cart.items" :key="item.id" class="cart-item">
            <input type="checkbox" :checked="cart.selected.has(item.id)" :aria-label="`Select ${item.title}`"
              @change="cart.toggle(item.id)" />
            <img :src="item.coverImage" :alt="item.title" class="cart-item__cover" />
            <div class="cart-item__info">
              <div class="cart-item__title">{{ item.title }}</div>
              <div class="muted">{{ money(item.price) }} &middot; Stock: {{ item.stock }}</div>
              <div class="cart-item__actions">
                <input type="number" min="1" :max="item.stock" :value="item.quantity" class="qty-input"
                  :disabled="busy" :aria-label="`Quantity of ${item.title}`" @change="changeQuantity(item, $event)" />
                <button type="button" class="link-button" :disabled="busy" @click="run(() => cart.remove(item.id))">
                  Remove
                </button>
              </div>
            </div>
            <div class="cart-item__subtotal">{{ money(item.subtotal) }}</div>
          </li>
        </ul>
      </template>
    </div>

    <div class="drawer__footer">
      <div>
        <div class="muted">Selected total</div>
        <div class="total">{{ money(cart.selectedTotal) }}</div>
      </div>
      <button type="button" class="button button--primary" :disabled="busy || !cart.selected.size" @click="checkout">
        Checkout ({{ cart.selectedItems.length }})
      </button>
    </div>
  </aside>
</template>
