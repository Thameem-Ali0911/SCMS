import React, { useState, useEffect, useCallback, useRef } from 'react'

/*
  Toast notification system — two exports:
    1. ToastContainer — mounts once in App.jsx, listens for 'scms:toast' events
    2. useToast       — hook for components that need to fire toasts directly

  MENTOR NOTE — event-driven vs prop-drilling approach:
  We need toasts from two sources:
    a) Axios interceptors (outside React tree) → custom DOM event
    b) Components (inside React tree) → useToast() hook

  Both funnel into the same ToastContainer state. This is cleaner than
  trying to pass a setToast function through props or context to every
  component that might need it.

  MENTOR NOTE — accessibility:
  aria-live="polite" tells screen readers to announce new toast messages
  without interrupting what they're currently reading. "assertive" would
  interrupt immediately — use that only for critical errors.

  Toast types: 'success' | 'error' | 'info' | 'warning'
  Each auto-dismisses after 4 seconds (configurable via duration prop).
*/

let toastId = 0

export function ToastContainer() {
  const [toasts, setToasts] = useState([])

  const add = useCallback((message, type = 'info', duration = 4000) => {
    const id = ++toastId
    setToasts(prev => [...prev, { id, message, type }])
    // Auto-dismiss
    setTimeout(() => {
      setToasts(prev => prev.filter(t => t.id !== id))
    }, duration)
  }, [])

  const dismiss = (id) => setToasts(prev => prev.filter(t => t.id !== id))

  // Listen for DOM events fired by axios interceptor
  useEffect(() => {
    const handler = (e) => {
      add(e.detail.message, e.detail.type ?? 'error')
    }
    window.addEventListener('scms:toast', handler)
    return () => window.removeEventListener('scms:toast', handler)
  }, [add])

  if (toasts.length === 0) return null

  return (
    <div
      aria-live="polite"
      style={{
        position: 'fixed',
        bottom: '1.5rem',
        right: '1.5rem',
        zIndex: 9999,
        display: 'flex',
        flexDirection: 'column',
        gap: '0.5rem',
        maxWidth: '360px',
        width: '100%',
      }}
    >
      {toasts.map(t => (
        <Toast key={t.id} toast={t} onDismiss={() => dismiss(t.id)} />
      ))}
    </div>
  )
}

function Toast({ toast, onDismiss }) {
  const styles = {
    success: { bg: '#F0FDF4', border: '#86EFAC', icon: '✓', color: '#166534' },
    error:   { bg: '#FEF2F2', border: '#FCA5A5', icon: '✕', color: '#991B1B' },
    warning: { bg: '#FFFBEB', border: '#FCD34D', icon: '!', color: '#92400E' },
    info:    { bg: '#EFF6FF', border: '#93C5FD', icon: 'i', color: '#1E40AF' },
  }
  const s = styles[toast.type] ?? styles.info

  return (
    <div
      role="alert"
      style={{
        display: 'flex',
        alignItems: 'flex-start',
        gap: '0.625rem',
        padding: '0.75rem 1rem',
        background: s.bg,
        border: `1px solid ${s.border}`,
        borderRadius: 8,
        boxShadow: '0 4px 16px rgba(0,0,0,0.12)',
        animation: 'toastIn 0.2s ease',
      }}
    >
      {/* Icon */}
      <span style={{
        width: 20, height: 20, borderRadius: '50%',
        background: s.color, color: '#fff',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        fontSize: '0.65rem', fontWeight: 700, flexShrink: 0, marginTop: 1,
      }}>
        {s.icon}
      </span>

      {/* Message */}
      <p style={{ flex: 1, fontSize: '0.85rem', color: s.color,
                  lineHeight: 1.45, margin: 0 }}>
        {toast.message}
      </p>

      {/* Dismiss */}
      <button
        onClick={onDismiss}
        aria-label="Dismiss notification"
        style={{
          background: 'none', border: 'none', cursor: 'pointer',
          color: s.color, opacity: 0.6, padding: 0, lineHeight: 1,
          fontSize: '0.9rem', flexShrink: 0,
        }}
      >
        ×
      </button>
    </div>
  )
}

/*
  useToast — hook for components that want to fire toasts directly.

  Usage:
    const toast = useToast()
    toast.success('Complaint resolved successfully.')
    toast.error('Failed to update status.')
    toast.info('Loading data…')
    toast.warning('You have 1 attempt remaining.')
*/
export function useToast() {
  const fire = useCallback((message, type = 'info', duration = 4000) => {
    window.dispatchEvent(new CustomEvent('scms:toast', {
      detail: { message, type, duration }
    }))
  }, [])

  return {
    success: (msg, dur) => fire(msg, 'success', dur),
    error:   (msg, dur) => fire(msg, 'error',   dur),
    info:    (msg, dur) => fire(msg, 'info',     dur),
    warning: (msg, dur) => fire(msg, 'warning',  dur),
  }
}
