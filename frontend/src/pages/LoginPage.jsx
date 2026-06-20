import React, { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../components/Toast'
import api from '../api/axios'
import BrandPanel from '../components/BrandPanel'

const MailIcon = () => (
  <svg viewBox="0 0 24 24" stroke="currentColor" fill="none"
    strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <rect x="2" y="4" width="20" height="16" rx="2" />
    <path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7" />
  </svg>
)
const LockIcon = () => (
  <svg viewBox="0 0 24 24" stroke="currentColor" fill="none"
    strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
    <path d="M7 11V7a5 5 0 0 1 10 0v4" />
  </svg>
)
const EyeIcon = ({ off }) => off ? (
  <svg viewBox="0 0 24 24" stroke="currentColor" fill="none"
    strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" width="16" height="16">
    <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24" />
    <line x1="1" y1="1" x2="23" y2="23" />
  </svg>
) : (
  <svg viewBox="0 0 24 24" stroke="currentColor" fill="none"
    strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" width="16" height="16">
    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
    <circle cx="12" cy="12" r="3" />
  </svg>
)
const AlertIcon = () => (
  <svg viewBox="0 0 24 24" stroke="currentColor" fill="none"
    strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" width="16" height="16">
    <circle cx="12" cy="12" r="10" />
    <line x1="12" y1="8" x2="12" y2="12" />
    <line x1="12" y1="16" x2="12.01" y2="16" />
  </svg>
)

export default function LoginPage() {
  const { login } = useAuth()
  const navigate  = useNavigate()
  const toast     = useToast()

  const [form,    setForm]    = useState({ email: '', password: '' })
  const [errors,  setErrors]  = useState({})
  const [apiErr,  setApiErr]  = useState('')
  const [loading, setLoading] = useState(false)
  const [showPw,  setShowPw]  = useState(false)

  /*
    CHANGE in v1.3 — lockout state:
    When the backend returns a 429 (TooManyAttemptsException), we store
    the lockout message separately so we can style it differently from a
    regular "wrong password" error — it gets a warning-coloured banner.
  */
  const [lockedOut, setLockedOut] = useState(false)

  const handle = e => {
    const { name, value } = e.target
    setForm(f => ({ ...f, [name]: value }))
    if (errors[name]) setErrors(e => ({ ...e, [name]: '' }))
    setApiErr('')
    setLockedOut(false)
  }

  const validate = () => {
    const e = {}
    if (!form.email)    e.email    = 'Email is required'
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email))
                        e.email    = 'Enter a valid email'
    if (!form.password) e.password = 'Password is required'
    return e
  }

  const submit = async evt => {
    evt.preventDefault()
    const e = validate()
    if (Object.keys(e).length) { setErrors(e); return }

    setLoading(true)
    setApiErr('')
    setLockedOut(false)

    try {
      const { data } = await api.post('/auth/login', {
        email:    form.email.trim().toLowerCase(),
        password: form.password,
      })
      login(data)
      navigate('/dashboard', { replace: true })
    } catch (err) {
      const status  = err.response?.status
      const message = err.response?.data?.message

      if (status === 429) {
        // Brute-force lockout — show a distinct warning banner
        setLockedOut(true)
        setApiErr(message ?? 'Too many failed attempts. Please wait before trying again.')
      } else {
        // Wrong credentials — show remaining attempts if backend included it
        setApiErr(message ?? 'Something went wrong. Please try again.')
      }
    } finally {
      setLoading(false)
    }
  }

  const fillDemo = (type) => {
    const demos = {
      admin:   { email: 'admin@scms.com',   password: 'Admin@1234' },
      staff:   { email: 'staff@scms.com',   password: 'Staff@1234' },
      student: { email: 'student@scms.com', password: 'User@1234'  },
    }
    setForm(demos[type])
    setErrors({})
    setApiErr('')
    setLockedOut(false)
  }

  return (
    <div className="auth-shell">
      <BrandPanel />

      <div className="auth-form-panel">
        <div className="auth-form-inner">
          <p className="form-eyebrow">Welcome back</p>
          <h1 className="form-title">Sign in to SCMS</h1>
          <p className="form-desc">
            Enter your college email to access your complaint portal.
          </p>

          {/* Error / lockout banner */}
          {apiErr && (
            <div className={`alert ${lockedOut ? 'alert-warning' : 'alert-error'}`}>
              <AlertIcon />
              <span>{apiErr}</span>
            </div>
          )}

          <form onSubmit={submit} noValidate>
            <div className="field-group">
              <label htmlFor="email">Email address</label>
              <div className="field-wrap">
                <MailIcon />
                <input
                  id="email" name="email" type="email"
                  className={`field-input${errors.email ? ' error-field' : ''}`}
                  placeholder="you@college.edu"
                  value={form.email}
                  onChange={handle}
                  autoFocus
                  autoComplete="email"
                  disabled={lockedOut}
                />
              </div>
              {errors.email && <p className="field-err"><AlertIcon />{errors.email}</p>}
            </div>

            <div className="field-group">
              <label htmlFor="password">Password</label>
              <div className="field-wrap">
                <LockIcon />
                <input
                  id="password" name="password"
                  type={showPw ? 'text' : 'password'}
                  className={`field-input${errors.password ? ' error-field' : ''}`}
                  placeholder="••••••••"
                  value={form.password}
                  onChange={handle}
                  autoComplete="current-password"
                  disabled={lockedOut}
                />
                <button type="button" className="field-toggle"
                  onClick={() => setShowPw(v => !v)}
                  title={showPw ? 'Hide password' : 'Show password'}>
                  <EyeIcon off={showPw} />
                </button>
              </div>
              {errors.password && <p className="field-err"><AlertIcon />{errors.password}</p>}
            </div>

            <button
              type="submit"
              className="btn-cta"
              disabled={loading || lockedOut}
            >
              {loading
                ? <><span className="btn-spinner" /> Signing in…</>
                : lockedOut
                  ? 'Account Locked'
                  : 'Sign in'}
            </button>
          </form>

          {/* Demo accounts — updated with STAFF */}
          <div className="divider">Quick demo</div>
          <div className="demo-pill">
            <strong>Try without registering</strong><br />
            <span onClick={() => fillDemo('admin')}
              style={{ cursor: 'pointer', color: 'var(--indigo)', fontWeight: 500 }}>
              Admin
            </span>
            {' — '}<code>admin@scms.com</code> / <code>Admin@1234</code>
            <br />
            <span onClick={() => fillDemo('staff')}
              style={{ cursor: 'pointer', color: 'var(--indigo)', fontWeight: 500 }}>
              Staff
            </span>
            {' — '}<code>staff@scms.com</code> / <code>Staff@1234</code>
            <br />
            <span onClick={() => fillDemo('student')}
              style={{ cursor: 'pointer', color: 'var(--indigo)', fontWeight: 500 }}>
              Student
            </span>
            {' — '}<code>student@scms.com</code> / <code>User@1234</code>
          </div>

          <p className="switch-link">
            No account? <Link to="/register">Create one →</Link>
          </p>
        </div>
      </div>
    </div>
  )
}
