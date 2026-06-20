import axios from 'axios'

/*
  axios.js — single axios instance + interceptors.

  CHANGE in v1.3 (Production hardening):

  Two interceptors added:

  ── REQUEST interceptor ──────────────────────────────────────────────────────
  Injects the JWT Authorization header on every request.
  Previously this was only set on login and re-read from localStorage on
  page reload. The interceptor ensures it's ALWAYS present, even if a
  component constructs a new axios instance, forgets to set the header,
  or the token was rotated.

  ── RESPONSE interceptor ─────────────────────────────────────────────────────
  Handles error responses globally:

    401 Unauthorized → JWT expired or invalid. Clear auth, redirect to /login.
        This is the most important interceptor: without it, a user with an
        expired token sees API calls silently fail with no explanation. With it,
        they're redirected to login automatically.

    429 Too Many Requests → Rate limit hit (brute force protection).
        Show a specific "locked out" message, not the generic error.

    All other errors → Extract error.response.data.message (our standard envelope)
        and fire a toast notification. Components no longer need try/catch just
        to show error messages — the interceptor handles it globally.

  MENTOR NOTE — why a single axios instance?
  If you use axios.get() (the global default) everywhere, you'd have to
  configure the baseURL and interceptors in every file. One instance = one
  configuration. Changing the API base URL in production? One line.

  MENTOR NOTE — circular dependency risk:
  Interceptors that import AuthContext or useNavigate run into circular
  import problems in React. The workaround:
    - Read/clear localStorage directly (no React context needed)
    - Use window.location.href for redirect (no React Router needed)
  This is intentional — interceptors live outside the React tree.

  MENTOR NOTE — toast integration:
  We fire a custom DOM event ('scms:toast') that the ToastContainer component
  listens to. This avoids importing React state/context into this module.
  The alternative (zustand store, event bus library) is cleaner in large apps
  but overkill here.
*/

const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
  timeout: 15_000,
})

// ── REQUEST interceptor ───────────────────────────────────────────────────────

api.interceptors.request.use(
  (config) => {
    try {
      const stored = localStorage.getItem('scms_auth')
      if (stored) {
        const { token } = JSON.parse(stored)
        if (token) {
          config.headers['Authorization'] = `Bearer ${token}`
        }
      }
    } catch {
      // Corrupted localStorage — ignore, the 401 interceptor will clean up
    }
    return config
  },
  (error) => Promise.reject(error)
)

// ── RESPONSE interceptor ──────────────────────────────────────────────────────

api.interceptors.response.use(
  // Successful response — pass through unchanged
  (response) => response,

  // Error response
  (error) => {
    const status  = error.response?.status
    const message = error.response?.data?.message

    if (status === 401) {
      // JWT expired or invalid → log out and redirect to login
      // Do NOT show a toast for this — the login page is the feedback.
      localStorage.removeItem('scms_auth')
      // Only redirect if we're not already on the login page
      if (!window.location.pathname.includes('/login')) {
        window.location.href = '/login?expired=true'
      }
      return Promise.reject(error)
    }

    if (status === 429) {
      // Rate limiting / brute-force lockout — specific message
      fireToast(message ?? 'Too many attempts. Please wait and try again.', 'error')
      return Promise.reject(error)
    }

    if (status === 403) {
      // Access denied — only toast if it's unexpected (not on the login page)
      if (!window.location.pathname.includes('/login')) {
        fireToast(message ?? 'You do not have permission to perform this action.', 'error')
      }
      return Promise.reject(error)
    }

    if (status >= 500) {
      fireToast('Server error. Please try again later.', 'error')
      return Promise.reject(error)
    }

    // 400, 404, and other client errors — let the component handle them
    // (they're usually validation errors that need inline display, not toasts)
    return Promise.reject(error)
  }
)

/**
 * Fires a custom DOM event that ToastContainer listens to.
 * Decouples the axios module from React state.
 */
function fireToast(message, type = 'error') {
  window.dispatchEvent(new CustomEvent('scms:toast', {
    detail: { message, type }
  }))
}

export default api
