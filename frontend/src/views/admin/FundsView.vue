<script setup>
import { onMounted, ref } from 'vue'

import { adminApi } from '../../api'
import { dateTime, money, statusLabel } from '../../utils/format'

const summary = ref(null)
const error = ref('')

onMounted(async () => {
  try {
    summary.value = await adminApi.funds()
  } catch (e) {
    error.value = e.message
  }
})
</script>

<template>
  <h1 class="page-title">Funds</h1>
  <p v-if="error" class="form-error">{{ error }}</p>

  <template v-if="summary">
    <div class="stats">
      <div class="stat card">
        <div class="stat__label">Local Net Total</div>
        <div class="stat__value">{{ money(summary.localNetTotal) }}</div>
        <div class="muted small">In {{ money(summary.totalIn) }} &middot; Out {{ money(summary.totalOut) }}</div>
      </div>
      <div class="stat card">
        <div class="stat__label">Alipay Balance</div>
        <div class="stat__value">{{ summary.alipayBalance === null ? 'Unavailable' : money(summary.alipayBalance) }}</div>
      </div>
      <div class="stat card">
        <div class="stat__label">Difference</div>
        <div class="stat__value">{{ summary.difference === null ? 'Unavailable' : money(summary.difference) }}</div>
      </div>
    </div>
    <p class="muted small">
      The Alipay balance comparison is for reference only. The sandbox account has its own starting balance,
      so the two figures are not expected to match.
    </p>

    <h2>Transactions</h2>
    <p v-if="!summary.transactions.length" class="muted">No transactions yet.</p>
    <div v-else class="table-wrap">
      <table class="table">
        <thead>
          <tr><th>Time</th><th>Type</th><th>Order</th><th class="num">Amount</th><th>Alipay Trade No.</th></tr>
        </thead>
        <tbody>
          <tr v-for="tx in summary.transactions" :key="tx.id">
            <td>{{ dateTime(tx.occurredAt) }}</td>
            <td>{{ statusLabel(tx.type) }}</td>
            <td>{{ tx.orderNo }}</td>
            <td :class="['num', tx.type === 'REFUND_OUT' ? 'amount--out' : 'amount--in']">
              {{ tx.type === 'REFUND_OUT' ? '-' : '+' }}{{ money(tx.amount) }}
            </td>
            <td>{{ tx.alipayTradeNo || '-' }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </template>
</template>
