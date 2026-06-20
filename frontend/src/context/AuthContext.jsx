import React, { createContext, useContext, useState, useEffect } from 'react'
import api from '../api/axios'

/*
  AuthContext — shares auth state across the whole React tree.

  What's stored per logged-in user:
    { token, userId, firstName, lastName, email, roles }

  On page reload: we rehydrate from localStorage so the user stays logged in.
  On login/register: we store to localStorage + set the axios Authorization header.
  On logout: we clear both.

  MENTOR NOTE — localStorage vs httpOnly cookie:
  localStorage is simple and works well for college projects. The more
  secure production pattern is httpOnly cookies (not accessible to JS,
  so XSS can't steal the token), but they require more backend setup.
*/
const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user,    setUser]    = useState(null)
  const [loading, setLoading] = useState(true)   // true while we check localStorage

  useEffect(() => {
    try {
      const stored = localStorage.getItem('scms_auth')
      if (stored) {
        const parsed = JSON.parse(stored)
        setUser(parsed)
        api.defaults.headers.common['Authorization'] = `Bearer ${parsed.token}`
      }
    } catch {
      localStorage.removeItem('scms_auth')
    }
    setLoading(false)
  }, [])

  const login = (userData) => {
    setUser(userData)
    localStorage.setItem('scms_auth', JSON.stringify(userData))
    api.defaults.headers.common['Authorization'] = `Bearer ${userData.token}`
  }

  const logout = () => {
    setUser(null)
    localStorage.removeItem('scms_auth')
    delete api.defaults.headers.common['Authorization']
  }

  const isAdmin = () => user?.roles?.includes('ADMIN') ?? false

  return (
    <AuthContext.Provider value={{ user, login, logout, isAdmin, loading }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be inside <AuthProvider>')
  return ctx
}
