import axios from 'axios'

/*
  axios.js — single axios instance + interceptors.

  CHANGE in v2.0 (production hardening):

  ── In-memory access token, not localStorage-read-per-request ──────────────
  setAccessToken()/getAccessToken() hold the token in a module-level
  variable. AuthContext calls setAccessToken() on login/refresh/logout. This
  avoids re-parsing localStorage JSON on every single API call (minor), and
  more importantly gives the refresh-on-401 interceptor below a single
  source of truth it can update immediately after a silent refresh, before
  AuthContext's React state has even re-rendered.

  ── withCredentials: true ────────────────────────────────────────────────────
  Required so the browser attaches the HttpOnly refresh-token cookie
  (scms_refresh, Path=/api/auth) to /api/auth/refresh and /api/auth/logout
  calls. See SecurityConfig.java's CORS configuration (allowCredentials)
  and AuthController.java for the cookie itself.

  ── Silent refresh on 401, with request queueing ───────────────────────────
  When an access token expires mid-session, the FIRST request that gets a
  401 triggers exactly one POST /auth/refresh call. Any OTHER requests that
  fail with 401 while that refresh is in flight are queued and retried
  automatically once the new access token arrives, instead of each firing
  its own redundant refresh call (which would race and potentially rotate
  the refresh cookie out from under each other).

  If the refresh itself fails (refresh token expired/revoked too), every
  queued request is rejected, local auth state is cleared, and the user is
  redirected to /login?expired=true — same UX as v1.3's immediate-401
  redirect, just one refresh attempt later.

  MENTOR NOTE — circular dependency avoidance (carried over from v1.3):
  This module intentionally does NOT import AuthContext or useNavigate —
  it manipulates localStorage directly and uses window.location for the
  final redirect. Interceptors live outside the React tree by design.
*/

let accessToken = null

export function setAccessToken(token) {
  accessToken = token
}

export function getAccessToken() {
  return accessToken
}

const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
  timeout: 15_000,
  withCredentials: true,
})

// ── REQUEST interceptor ───────────────────────────────────────────────────────

api.interceptors.request.use(
  (config) => {
    if (accessToken) {
      config.headers['Authorization'] = `Bearer ${accessToken}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// ── Refresh queueing state ────────────────────────────────────────────────────

let isRefreshing = false
let pendingQueue = [] // { resolve, reject, config }

function resolveQueue(newToken) {
  pendingQueue.forEach(({ resolve, config }) => {
    config.headers['Authorization'] = `Bearer ${newToken}`
    resolve(api(config))
  })
  pendingQueue = []
}

function rejectQueue(error) {
  pendingQueue.forEach(({ reject }) => reject(error))
  pendingQueue = []
}

function isAuthEndpoint(url = '') {
  return url.includes('/auth/login') || url.includes('/auth/register') ||
         url.includes('/auth/refresh') || url.includes('/auth/logout')
}

function clearSessionAndRedirect() {
  setAccessToken(null)
  localStorage.removeItem('scms_auth')
  if (!window.location.pathname.includes('/login')) {
    window.location.href = '/login?expired=true'
  }
}

// ── RESPONSE interceptor ──────────────────────────────────────────────────────

api.interceptors.response.use(
  (response) => response,

  async (error) => {
    const status   = error.response?.status
    const message  = error.response?.data?.message
    const original = error.config

    if (status === 401 && original && !isAuthEndpoint(original.url) && !original._retried) {
      original._retried = true

      if (isRefreshing) {
        // A refresh is already in flight — queue this request behind it.
        return new Promise((resolve, reject) => {
          pendingQueue.push({ resolve, reject, config: original })
        })
      }

      isRefreshing = true
      try {
        const { data } = await api.post('/auth/refresh')
        setAccessToken(data.accessToken)
        localStorage.setItem('scms_auth', JSON.stringify(data))
        resolveQueue(data.accessToken)
        original.headers['Authorization'] = `Bearer ${data.accessToken}`
        return api(original)
      } catch (refreshError) {
        rejectQueue(refreshError)
        clearSessionAndRedirect()
        return Promise.reject(refreshError)
      } finally {
        isRefreshing = false
      }
    }

    if (status === 401) {
      // Either the refresh endpoint itself failed, or we already retried once.
      clearSessionAndRedirect()
      return Promise.reject(error)
    }

    if (status === 429) {
      fireToast(message ?? 'Too many attempts. Please wait and try again.', 'error')
      return Promise.reject(error)
    }

    if (status === 403) {
      if (!window.location.pathname.includes('/login')) {
        fireToast(message ?? 'You do not have permission to perform this action.', 'error')
      }
      return Promise.reject(error)
    }

    if (status >= 500) {
      fireToast('Server error. Please try again later.', 'error')
      return Promise.reject(error)
    }

    // 400, 404, 409, and other client errors — let the component handle them
    // (usually validation/conflict errors that need inline display, not toasts)
    return Promise.reject(error)
  }
)

/** Fires a custom DOM event that ToastContainer listens to. */
function fireToast(message, type = 'error') {
  window.dispatchEvent(new CustomEvent('scms:toast', {
    detail: { message, type }
  }))
}

export default api
