import http from './http'

export function login(data) {
  return http.post('/v1/auth/login', data)
}

export function getCurrentUser() {
  return http.get('/v1/auth/me')
}
