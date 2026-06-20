import React, { useEffect } from 'react'
import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom'
import { AuthProvider, useAuth } from './context/AuthContext'
import ErrorBoundary             from './components/ErrorBoundary'
import { ToastContainer }        from './components/Toast'
import LoginPage                 from './pages/LoginPage'
import RegisterPage              from './pages/RegisterPage'
import Dashboard                 from './pages/Dashboard'
import ComplaintList             from './pages/ComplaintList'
import NewComplaint              from './pages/NewComplaint'
import ComplaintDetail           from './pages/ComplaintDetail'
import AdminUsers                from './pages/AdminUsers'
import AdminReports              from './pages/AdminReports'
import StaffQueue                from './pages/StaffQueue'

/*
  App.jsx — root component.

  CHANGE in v1.3 (Production hardening):
    • ErrorBoundary wraps the entire app — any JS crash shows a friendly UI
      instead of a blank white screen
    • ToastContainer is mounted here (once, globally) — all toast notifications
      from components and axios interceptors appear in the bottom-right corner
    • ExpiredSessionNotice — if the axios interceptor detected a 401 and
      redirected to /login?expired=true, show a dismissible banner
    • AdminRoute — frontend guard for ADMIN-only pages
    • StaffRoute — frontend guard for STAFF and ADMIN pages
*/

function ProtectedRoute({ children }) {
  const { user, loading } = useAuth()
  if (loading) return <div className="splash">Loading…</div>
  if (!user)   return <Navigate to="/login" replace />
  return children
}

function AdminRoute({ children }) {
  const { user, loading, isAdmin } = useAuth()
  if (loading)    return <div className="splash">Loading…</div>
  if (!user)      return <Navigate to="/login"     replace />
  if (!isAdmin()) return <Navigate to="/dashboard" replace />
  return children
}

function StaffRoute({ children }) {
  const { user, loading, isStaffOrAdmin } = useAuth()
  if (loading)          return <div className="splash">Loading…</div>
  if (!user)            return <Navigate to="/login"     replace />
  if (!isStaffOrAdmin()) return <Navigate to="/dashboard" replace />
  return children
}

/*
  ExpiredSessionNotice — shown when the URL contains ?expired=true.
  The axios interceptor adds this query param when it detects a 401.
  The banner tells the user WHY they were logged out instead of silently
  landing on the login page with no explanation.
*/
function ExpiredSessionNotice() {
  const location = useLocation()
  const { fireToast } = useAuth()

  useEffect(() => {
    if (location.search.includes('expired=true')) {
      window.dispatchEvent(new CustomEvent('scms:toast', {
        detail: {
          message: 'Your session expired. Please sign in again.',
          type: 'warning',
        }
      }))
      // Clean the URL without reloading
      window.history.replaceState({}, '', '/login')
    }
  }, [location.search])

  return null
}

function AppRoutes() {
  const { user } = useAuth()

  return (
    <>
      <ExpiredSessionNotice />
      <Routes>
        {/* ── Public ──────────────────────────────────────────────────── */}
        <Route path="/login"
          element={user ? <Navigate to="/dashboard" /> : <LoginPage />} />
        <Route path="/register"
          element={user ? <Navigate to="/dashboard" /> : <RegisterPage />} />

        {/* ── Any authenticated role ───────────────────────────────────── */}
        <Route path="/dashboard"
          element={<ProtectedRoute><Dashboard /></ProtectedRoute>} />
        <Route path="/complaints"
          element={<ProtectedRoute><ComplaintList /></ProtectedRoute>} />
        <Route path="/complaints/new"
          element={<ProtectedRoute><NewComplaint /></ProtectedRoute>} />
        <Route path="/complaints/:id"
          element={<ProtectedRoute><ComplaintDetail /></ProtectedRoute>} />

        {/* ── Staff + Admin (StaffRoute) ──────────────────────────────── */}
        <Route path="/queue"
          element={<StaffRoute><StaffQueue /></StaffRoute>} />

        {/* ── Admin only (AdminRoute) ──────────────────────────────────── */}
        <Route path="/users"
          element={<AdminRoute><AdminUsers /></AdminRoute>} />
        <Route path="/reports"
          element={<AdminRoute><AdminReports /></AdminRoute>} />

        {/* ── Catch-all ─────────────────────────────────────────────────── */}
        <Route path="*"
          element={<Navigate to={user ? '/dashboard' : '/login'} replace />} />
      </Routes>
    </>
  )
}

export default function App() {
  return (
    /*
      ErrorBoundary at the outermost level catches any JS crash in the
      entire component tree — including crashes in AuthProvider, Router,
      and all pages. Without it, a single null reference error gives the
      user a blank white page with no indication of what happened.
    */
    <ErrorBoundary>
      <AuthProvider>
        <BrowserRouter>
          <AppRoutes />
          {/*
            ToastContainer lives here — outside all routes — so it persists
            across page navigations. If it were inside a route component,
            toasts would disappear when the route changed.
          */}
          <ToastContainer />
        </BrowserRouter>
      </AuthProvider>
    </ErrorBoundary>
  )
}
