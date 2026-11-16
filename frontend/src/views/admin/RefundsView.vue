<script setup>
import { onMounted, ref } from 'vue'

import { adminApi } from '../../api'
import StatusBadge from '../../components/StatusBadge.vue'
import { useToastStore } from '../../stores/toast'
import { dateTime, money } from '../../utils/format'

const toast = useToastStore()
const requests = ref([])
const loading = ref(true)
const error = ref('')
const busyId = ref(null)

async function load() {
  try {
    requests.value = await adminApi.refundRequests()
    error.value = ''
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function review(request, approve) {
  const verb = approve ? 'Approve' : 'Reject'
  const detail = approve
    ? `refund ${money(request.refundAmount)} through Alipay`
    : 'reject this request and complete the order'
  if (!window.confirm(`${verb} the refund for order ${request.order.orderNo}? This will ${detail}.`)) return
  busyId.value = request.id
  try {
    await (approve ? adminApi.approveRefund(request.id) : adminApi.rejectRefund(request.id))
    toast.success(approve ? 'Refund completed' : 'Refund request rejected')
  } catch (e) {
    toast.error(e.message)
  } finally {
    busyId.value = null
    await load()
  }
}
</script>

<template>
  <h1 class="page-title">Refund Requests</h1>
  <p v-if="error" class="form-error">{{ error }}</p>
  <p v-else-if="!loading && !requests.length" class="muted">There are no refund requests.</p>

  <div v-else class="table-wrap">
    <table class="table">
      <thead>
        <tr>
          <th>Requested</th>
          <th>Order</th>
          <th>Books</th>
          <th class="num">Amount</th>
          <th>Reason</th>
          <th>Status</th>
          <th>Actions</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="request in requests" :key="request.id">
          <td>{{ dateTime(request.createdAt) }}</td>
          <td>
            <div>{{ request.order.orderNo }}</div>
            <StatusBadge :status="request.order.status" />
          </td>
          <td>
            <div v-for="item in request.order.items" :key="item.bookId">{{ item.title }} &times; {{ item.quantity }}</div>
          </td>
          <td class="num">{{ money(request.refundAmount) }}</td>
          <td class="reason">{{ request.reason }}</td>
          <td>
            <StatusBadge :status="request.status" />
            <div v-if="request.reviewedAt" class="muted small">{{ dateTime(request.reviewedAt) }}</div>
            <div v-if="request.lastError" class="form-error small">Last attempt failed: {{ request.lastError }}</div>
          </td>
          <td>
            <div v-if="request.status === 'PENDING_REVIEW'" class="actions">
              <button type="button" class="button button--primary" :disabled="busyId === request.id"
                @click="review(request, true)">
                {{ request.lastError ? 'Retry Refund' : 'Approve' }}
              </button>
              <button type="button" class="button" :disabled="busyId === request.id" @click="review(request, false)">
                Reject
              </button>
            </div>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
