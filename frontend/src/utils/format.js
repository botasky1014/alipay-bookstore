const moneyFormat = new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

export function money(value) {
  if (value === null || value === undefined) return '-'
  return `¥${moneyFormat.format(Number(value))}`
}

export function dateTime(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('en-US', {
    year: 'numeric',
    month: 'short',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}

const LABELS = {
  PENDING_PAYMENT: 'Pending Payment',
  PAID: 'Paid',
  REFUNDING: 'Refunding',
  REFUNDED: 'Refunded',
  CLOSED: 'Closed',
  COMPLETED: 'Completed',
  PENDING_REVIEW: 'Pending Review',
  REJECTED: 'Rejected',
  PAYMENT_IN: 'Payment In',
  REFUND_OUT: 'Refund Out',
}

export function statusLabel(status) {
  return LABELS[status] || status
}
