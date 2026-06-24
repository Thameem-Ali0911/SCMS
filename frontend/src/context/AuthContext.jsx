import React, { createContext, useContext, useState, useEffect, useCallback } from 'react'
import api, { setAccessToken } from '../api/axios'

/*
  AuthContext — shares auth state across the whole React tree.

  CHANGE in v2.0 (production hardening):

  What's stored in localStorage now is ONLY the access token + profile info
  — { accessToken, userId, firstName, lastName, email, roles }. The refresh
  token is no longer part of this object at all: the backend sets it as an
  HttpOnly cookie (see AuthController.java), which JavaScript cannot read.
  This is the fix for the v1.3 finding "JWT in localStorage is vulnerable to
  XSS token theft" — the long-lived credential simply isn't reachable by a
  script anymore, even a malicious one.

  On mount, if we have a remembered user, we proactively call
  POST /auth/refresh (the browser attaches the HttpOnly cookie automatically)
  to get a fresh access token — this means closing the tab and reopening it
  the next day still works without forcing a re-login, even though the
  access token itself only lives 15 minutes.

  CHANGE in v2.0: isStaff() / isStaffOrAdmin() added — v1.3 had StaffRoute
  in App.jsx calling isStaffOrAdmin() which did not exist on this context at
  all, causing a guaranteed runtime TypeError for any user navigating to
  /queue. Both helpers exist now, following the same pattern as isAdmin().
*/
const AuthContext = createContext(null)

const STORAGE_KEY = 'scms_auth'

export function AuthProvider({ children }) {
  const [user,    setUser]    = useState(null)
  const [loading, setLoading] = useState(true) // true while we attempt silent refresh

  useEffect(() => {
    let stored = null
    try {
      const raw = localStorage.getItem(STORAGE_KEY)
      if (raw) stored = JSON.parse(raw)
    } catch {
      localStorage.removeItem(STORAGE_KEY)
    }

    if (!stored) {
      setLoading(false)
      return
    }

    // Optimistically show the remembered user immediately (snappy UI),
    // then silently verify/refresh the access token in the background.
    setUser(stored)
    setAccessToken(stored.accessToken)

    api.post('/auth/refresh')
      .then(({ data }) => {
        setUser(data)
        setAccessToken(data.accessToken)
        localStorage.setItem(STORAGE_KEY, JSON.stringify(data))
      })
      .catch(() => {
        // Refresh cookie missing/expired/revoked — treat as logged out.
        setUser(null)
        setAccessToken(null)
        localStorage.removeItem(STORAGE_KEY)
      })
      .finally(() => setLoading(false))
  }, [])

  const login = useCallback((userData) => {
    setUser(userData)
    setAccessToken(userData.accessToken)
    localStorage.setItem(STORAGE_KEY, JSON.stringify(userData))
  }, [])

  const logout = useCallback(() => {
    // Best-effort — revokes the refresh token server-side (tokenVersion bump)
    // so it can't be replayed even if it leaked. We clear local state
    // regardless of whether this call succeeds.
    api.post('/auth/logout').catch(() => {})
    setUser(null)
    setAccessToken(null)
    localStorage.removeItem(STORAGE_KEY)
  }, [])

  /** Called by the axios refresh interceptor after a successful silent refresh. */
  const updateSession = useCallback((data) => {
    setUser(data)
    setAccessToken(data.accessToken)
    localStorage.setItem(STORAGE_KEY, JSON.stringify(data))
  }, [])

  /** Called by the axios refresh interceptor if a silent refresh fails. */
  const clearSession = useCallback(() => {
    setUser(null)
    setAccessToken(null)
    localStorage.removeItem(STORAGE_KEY)
  }, [])

  const isAdmin        = () => user?.roles?.includes('ADMIN') ?? false
  const isStaff         = () => user?.roles?.includes('STAFF') ?? false
  const isStaffOrAdmin = () => isStaff() || isAdmin()

  return (
    <AuthContext.Provider value={{
      user, loading, login, logout, updateSession, clearSession,
      isAdmin, isStaff, isStaffOrAdmin,
    }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be inside <AuthProvider>')
  return ctx
}
