/**
 * Typed API client wrapper.
 *
 * Regenerate the underlying schema types with:
 *   npm run generate-api
 *
 * The generated `schema.d.ts` is NOT committed to git.
 * This client works as a generic typed fetch wrapper usable with or
 * without the generated types.
 */

const API_BASE = '/api'

// ---------------------------------------------------------------------------
// Generic helpers (no dependency on generated schema)
// ---------------------------------------------------------------------------

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public body?: unknown,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

interface RequestOptions {
  signal?: AbortSignal
}

async function request<T>(
  method: 'GET' | 'POST' | 'PUT' | 'DELETE',
  url: string,
  body?: unknown,
  opts?: RequestOptions,
): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  }
  const res = await fetch(API_BASE + url, {
    method,
    headers,
    body: body != null ? JSON.stringify(body) : undefined,
    signal: opts?.signal,
  })
  if (!res.ok) {
    const err = await res.json().catch(() => ({ message: res.statusText }))
    throw new ApiError(res.status, err.message ?? res.statusText, err)
  }
  return res.json() as Promise<T>
}

// ---------------------------------------------------------------------------
// Typed API helpers — pass the generated `paths` type for full type safety
// ---------------------------------------------------------------------------

/**
 * Create a typed API client using the generated OpenAPI paths type.
 *
 * Usage:
 *   import type { paths } from './schema.d.ts'
 *   import { createTypedClient } from './client'
 *   const api = createTypedClient<paths>()
 *   const tasks = await api.get('/api/tasks')
 */
export function createTypedClient<Paths>() {
  return {
    get<Url extends string>(url: Url, opts?: RequestOptions): Promise<unknown> {
      return request('GET', url, undefined, opts)
    },
    post<Url extends string>(url: Url, body?: unknown, opts?: RequestOptions): Promise<unknown> {
      return request('POST', url, body, opts)
    },
    put<Url extends string>(url: Url, body?: unknown, opts?: RequestOptions): Promise<unknown> {
      return request('PUT', url, body, opts)
    },
    delete<Url extends string>(url: Url, opts?: RequestOptions): Promise<unknown> {
      return request('DELETE', url, undefined, opts)
    },
  }
}

// ---------------------------------------------------------------------------
// Untyped convenience export — drop-in for quick use without generated types
// ---------------------------------------------------------------------------

export const api = {
  get: <T>(url: string, opts?: RequestOptions) => request<T>('GET', url, undefined, opts),
  post: <T>(url: string, body?: unknown, opts?: RequestOptions) => request<T>('POST', url, body, opts),
  put: <T>(url: string, body?: unknown, opts?: RequestOptions) => request<T>('PUT', url, body, opts),
  delete: <T>(url: string, opts?: RequestOptions) => request<T>('DELETE', url, undefined, opts),
} as const
