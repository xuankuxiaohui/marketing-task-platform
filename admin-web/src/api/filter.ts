import { http } from './http'

export function validateFilter(expression: string) {
  return http.post('/admin/filter/validate', { expression })
}
