import { orderApi } from '../api'
import { useToastStore } from '../stores/toast'

/**
 * Starts Alipay web payment for an order. The backend returns an auto-submit form; inserting and
 * submitting it sends the browser to the Alipay cashier page, which leaves this site.
 * Throws an Error with the backend message when the payment cannot be started.
 */
export async function payOrder(orderId) {
  const result = await orderApi.pay(orderId)
  if (result.status === 'PAID') {
    useToastStore().success('This order has already been paid.')
    return
  }
  const container = document.createElement('div')
  container.style.display = 'none'
  container.innerHTML = result.formHtml
  document.body.appendChild(container)
  const form = container.querySelector('form')
  if (!form) throw new Error('Invalid payment form')
  form.submit()
}
