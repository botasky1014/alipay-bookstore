<script setup>
import { onMounted, ref } from 'vue'

import { orderApi } from '../api'
import ModalDialog from '../components/ModalDialog.vue'
import StatusBadge from '../components/StatusBadge.vue'
import { usePaymentStore } from '../stores/payment'
import { useSessionStore } from '../stores/session'
import { useToastStore } from '../stores/toast'
import { dateTime, money } from '../utils/format'

const session = useSessionStore()
const payment = usePaymentStore()
const toast = useToastStore()

const orders = ref([])
const loading = ref(true)
const error = ref('')
const busyId = ref(null)
const refundOrder = ref(null)
const refundReason = ref('')
const refundError = ref('')

async function load() {
  try {
    orders.value = await orderApi.list(session.user.id)
    error.value = ''
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function closeOrder(order) {
  if (!window.confirm(`Close order ${order.orderNo}? The books will be returned to stock.`)) return
  busyId.value = order.id
  try {
    await orderApi.close(order.id)
    toast.success('Order closed')
  } catch (e) {
    toast.error(e.message)
  } finally {
    busyId.value = null
    await load()
  }
}

function openRefund(order) {
  refundOrder.value = order
  refundReason.value = ''
  refundError.value = ''
}

async function submitRefund() {
  if (!refundReason.value.trim()) {
    refundError.value = 'Please enter a reason'
    return
  }
  busyId.value = refundOrder.value.id
  try {
    await orderApi.requestRefund(refundOrder.value.id, refundReason.value.trim())
    toast.success('Refund requested. An administrator will review it.')
    refundOrder.value = null
  } catch (e) {
    refundError.value = e.message
  } finally {
    busyId.value = null
    await load()
  }
}
</script>

<template>
  <h1 class="page-title">My Orders</h1>
  <p v-if="error" class="form-error">{{ error }}</p>
  <p v-else-if="!loading && !orders.length" class="muted">You have no orders yet.</p>

  <article v-for="order in orders" :key="order.id" class="card order">
    <header class="order__header">
      <div>
        <div class="order__no">Order {{ order.orderNo }}</div>
        <div class="muted">Placed {{ dateTime(order.createdAt) }}</div>
      </div>
      <StatusBadge :status="order.status" />
    </header>

    <table class="table">
      <thead>
        <tr><th>Book</th><th class="num">Unit price</th><th class="num">Qty</th><th class="num">Subtotal</th></tr>
      </thead>
      <tbody>
        <tr v-for="item in order.items" :key="item.bookId">
          <td>{{ item.title }}</td>
          <td class="num">{{ money(item.unitPrice) }}</td>
          <td class="num">{{ item.quantity }}</td>
          <td class="num">{{ money(item.subtotal) }}</td>
        </tr>
      </tbody>
    </table>

    <div class="order__details muted">
      <span v-if="order.paidAt">Paid {{ dateTime(order.paidAt) }}</span>
      <span v-if="order.completedAt">Completed {{ dateTime(order.completedAt) }}</span>
      <span v-if="order.closedAt">Closed {{ dateTime(order.closedAt) }} ({{ order.closeReason }})</span>
      <span v-if="order.refund">
        Refund: <StatusBadge :status="order.refund.status" /> &ldquo;{{ order.refund.reason }}&rdquo;
      </span>
    </div>

    <footer class="order__footer">
      <div class="total">Total {{ money(order.totalAmount) }}</div>
      <div class="actions">
        <template v-if="order.status === 'PENDING_PAYMENT'">
          <button type="button" class="button" :disabled="busyId === order.id" @click="closeOrder(order)">
            Close Order
          </button>
          <button type="button" class="button button--primary" :disabled="busyId === order.id"
            @click="payment.choose(order.id)">
            Pay
          </button>
        </template>
        <button v-if="order.status === 'PAID'" type="button" class="button" :disabled="busyId === order.id"
          @click="openRefund(order)">
          Request Refund
        </button>
      </div>
    </footer>
  </article>

  <ModalDialog v-if="refundOrder" title="Request a refund" @close="refundOrder = null">
    <p class="muted">
      Order {{ refundOrder.orderNo }} &middot; Full refund of {{ money(refundOrder.totalAmount) }}.
      A refund can be requested only once.
    </p>
    <label class="field">
      <span>Reason</span>
      <textarea v-model="refundReason" rows="4" maxlength="500" placeholder="Why do you want a refund?" />
    </label>
    <p v-if="refundError" class="form-error">{{ refundError }}</p>
    <div class="modal__footer">
      <button type="button" class="button" @click="refundOrder = null">Cancel</button>
      <button type="button" class="button button--primary" :disabled="busyId === refundOrder.id"
        @click="submitRefund">
        Submit Request
      </button>
    </div>
  </ModalDialog>
</template>
