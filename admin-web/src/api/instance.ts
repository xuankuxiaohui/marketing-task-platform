import { http } from './http'

export function listInstances() {
  return http.get('/admin/instance')
}
