import axios from 'axios'

const http = axios.create({
  baseURL: '/api',
  timeout: 30000
})

http.interceptors.response.use(
  res => res.data,
  err => Promise.reject(err)
)

export default http
