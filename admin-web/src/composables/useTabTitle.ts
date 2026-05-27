import type { RouteLocationNormalized } from 'vue-router'

export function resolveTabTitle(route: RouteLocationNormalized): string {
  const meta = route.meta
  if (typeof meta.title === 'function') {
    return meta.title(route)
  }
  if (typeof meta.title === 'string') {
    return meta.title
  }
  const segments = route.path.split('/').filter(Boolean)
  return segments[segments.length - 1] ?? route.path
}
