import http from './http'

const data = (promise) => promise.then((response) => response.data)

export const authApi = {
  login: (username) => data(http.post('/auth/login', { username })),
}

export const bookApi = {
  list: () => data(http.get('/books')),
  get: (id) => data(http.get(`/books/${id}`)),
}

export const cartApi = {
  get: (userId) => data(http.get('/cart', { params: { userId } })),
  add: (userId, bookId, quantity) => data(http.post('/cart/items', { userId, bookId, quantity })),
  update: (itemId, quantity) => data(http.put(`/cart/items/${itemId}`, { quantity })),
  remove: (itemId) => data(http.delete(`/cart/items/${itemId}`)),
}

export const orderApi = {
  create: (userId, items, fromCart) => data(http.post('/orders', { userId, items, fromCart })),
  list: (userId) => data(http.get('/orders', { params: { userId } })),
  get: (id) => data(http.get(`/orders/${id}`)),
  pay: (id) => data(http.post(`/orders/${id}/pay`)),
  close: (id) => data(http.post(`/orders/${id}/close`)),
  requestRefund: (id, reason) => data(http.post(`/orders/${id}/refund-requests`, { reason })),
  returnCheck: (params) => data(http.post('/alipay/return-check', params)),
}

export const adminApi = {
  updateBook: (id, changes) => data(http.patch(`/admin/books/${id}`, changes)),
  refundRequests: () => data(http.get('/admin/refund-requests')),
  approveRefund: (id) => data(http.post(`/admin/refund-requests/${id}/approve`)),
  rejectRefund: (id) => data(http.post(`/admin/refund-requests/${id}/reject`)),
  funds: () => data(http.get('/admin/funds')),
  getAlipayUrls: () => data(http.get('/admin/settings/alipay-urls')),
  updateAlipayUrls: (notifyUrl, returnUrl) => data(http.put('/admin/settings/alipay-urls', { notifyUrl, returnUrl })),
}
