<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'

import { orderApi } from '../api'
import { usePaymentStore } from '../stores/payment'
import { useToastStore } from '../stores/toast'
import ModalDialog from './ModalDialog.vue'

const payment = usePaymentStore()
const toast = useToastStore()
const router = useRouter()
const busy = ref(false)

/** Inserts the Alipay form returned by the backend and submits it, which leaves this site. */
function submitAlipayForm(formHtml) {
  const container = document.createElement('div')
  container.style.display = 'none'
  container.innerHTML = formHtml
  document.body.appendChild(container)
  const form = container.querySelector('form')
  if (!form) throw new Error('Invalid payment form')
  form.submit()
}

async function pay(method) {
  busy.value = true
  try {
    const result = await orderApi.pay(payment.orderId, method)
    if (result.status === 'PAID') {
      payment.cancel()
      toast.success('This order has already been paid.')
      router.push('/orders')
      return
    }
    submitAlipayForm(result.formHtml)
  } catch (error) {
    payment.cancel()
    toast.error(error.message)
    router.push('/orders')
  } finally {
    busy.value = false
  }
}

function later() {
  payment.cancel()
  router.push('/orders')
}
</script>

<template>
  <ModalDialog v-if="payment.orderId" title="Choose a payment method" @close="later">
    <p class="muted">You will be redirected to Alipay to complete the payment.</p>
    <div class="pay-options">
      <button type="button" class="pay-option" :disabled="busy" @click="pay('WEB')">
        <strong>Alipay Web Payment</strong>
        <span>Log in to your Alipay account on the cashier page</span>
      </button>
      <button type="button" class="pay-option" :disabled="busy" @click="pay('QR')">
        <strong>Alipay QR Code</strong>
        <span>Scan the QR code with the Alipay app</span>
      </button>
    </div>
    <div class="modal__footer">
      <button type="button" class="button" :disabled="busy" @click="later">Pay later</button>
    </div>
  </ModalDialog>
</template>
