import axios from 'axios'

// All requests go through the same origin; nginx (or the Vite dev proxy)
// forwards /api to the backend.
const http = axios.create({ baseURL: '/api', timeout: 30000 })

// Turn backend {code, message} errors into an Error with a readable message.
http.interceptors.response.use(
  (response) => response,
  (error) => {
    const data = error.response?.data
    const message =
      (data && typeof data === 'object' && data.message) ||
      (error.code === 'ECONNABORTED' ? 'The request timed out' : 'Unable to reach the server')
    const wrapped = new Error(message)
    wrapped.code = data?.code
    wrapped.status = error.response?.status
    return Promise.reject(wrapped)
  },
)

export default http
