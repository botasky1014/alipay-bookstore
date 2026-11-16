import { defineStore } from 'pinia'
import { ref } from 'vue'

/** Controls the "choose payment method" dialog, which can be opened from any page. */
export const usePaymentStore = defineStore('payment', () => {
  const orderId = ref(null)

  function choose(id) {
    orderId.value = id
  }

  function cancel() {
    orderId.value = null
  }

  return { orderId, choose, cancel }
})
