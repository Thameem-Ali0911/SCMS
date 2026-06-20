import React, { useState, useMemo } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import api from '../api/axios'
import BrandPanel from '../components/BrandPanel'

/* ── SVG icons ─────────────────────────────────────────── */
const UserIcon = () => (
  <svg viewBox="0 0 24 24" stroke="currentColor" fill="none"
       strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
    <circle cx="12" cy="7" r="4"/>
  </svg>
)

const MailIcon = () => (
  <svg viewBox="0 0 24 24" stroke="currentColor" fill="none"
       strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <rect x="2" y="4" width="20" height="16" rx="2"/>
    <path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7"/>
  </svg>
)

const LockIcon = () => (
  <svg viewBox="0 0 24 24" stroke="currentColor" fill="none"
       strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
    <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
  </svg>
)

const PhoneIcon = () => (
  <svg viewBox="0 0 24 24" stroke="currentColor" fill="none"
       strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07A19.5 19.5 0 0 1 4.69 13a19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 3.62 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"/>
  </svg>
)

const EyeIcon = ({ off }) => off ? (
  <svg viewBox="0 0 24 24" stroke="currentColor" fill="none"
       strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" width="16" height="16">
    <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
    <line x1="1" y1="1" x2="23" y2="23"/>
  </svg>
) : (
  <svg viewBox="0 0 24 24" stroke="currentColor" fill="none"
       strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" width="16" height="16">
    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
    <circle cx="12" cy="12" r="3"/>
  </svg>
)

const AlertIcon = () => (
  <svg viewBox="0 0 24 24" stroke="currentColor" fill="none"
       strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" width="16" height="16">
    <circle cx="12" cy="12" r="10"/>
    <line x1="12" y1="8" x2="12" y2="12"/>
    <line x1="12" y1="16" x2="12.01" y2="16"/>
  </svg>
)

const CheckIcon = () => (
  <svg viewBox="0 0 24 24" stroke="currentColor" fill="none"
       strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" width="16" height="16">
    <polyline points="20 6 9 17 4 12"/>
  </svg>
)

/* ── Password strength scorer ──────────────────────────── */
function getStrength(pw) {
  if (!pw) return { score: 0, label: '' }
  let s = 0
  if (pw.length >= 8)                          s++
  if (pw.length >= 12)                         s++
  if (/[A-Z]/.test(pw) && /[a-z]/.test(pw))   s++
  if (/\d/.test(pw))                           s++
  if (/[^A-Za-z0-9]/.test(pw))                s++
  if (s <= 1) return { score: 1, label: 'Weak' }
  if (s === 2) return { score: 2, label: 'Fair' }
  if (s === 3) return { score: 3, label: 'Good' }
  return { score: 4, label: 'Strong' }
}

const STRENGTH_CLASSES = ['', 'weak', 'fair', 'good', 'strong']

export default function RegisterPage() {
  const { login } = useAuth()
  const navigate  = useNavigate()

  const [form, setForm] = useState({
    firstName: '', lastName: '', email: '',
    password: '', phone: '',
  })
  const [errors,  setErrors]  = useState({})
  const [apiErr,  setApiErr]  = useState('')
  const [loading, setLoading] = useState(false)
  const [showPw,  setShowPw]  = useState(false)
  const [done,    setDone]    = useState(false)

  const strength = useMemo(() => getStrength(form.password), [form.password])

  const handle = e => {
    const { name, value } = e.target
    setForm(f => ({ ...f, [name]: value }))
    if (errors[name]) setErrors(e => ({ ...e, [name]: '' }))
    setApiErr('')
  }

  const validate = () => {
    const e = {}
    if (!form.firstName.trim())     e.firstName = 'Required'
    if (!form.lastName.trim())      e.lastName  = 'Required'
    if (!form.email)                e.email     = 'Email is required'
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email))
                                    e.email     = 'Enter a valid email'
    if (!form.password)             e.password  = 'Password is required'
    else if (form.password.length < 8)
                                    e.password  = 'Must be at least 8 characters'
    else if (!/(?=.*[A-Za-z])(?=.*\d)/.test(form.password))
                                    e.password  = 'Needs a letter and a number'
    return e
  }

  const submit = async evt => {
    evt.preventDefault()
    const e = validate()
    if (Object.keys(e).length) { setErrors(e); return }

    setLoading(true)
    setApiErr('')
    try {
      const { data } = await api.post('/auth/register', {
        firstName: form.firstName.trim(),
        lastName:  form.lastName.trim(),
        email:     form.email.trim().toLowerCase(),
        password:  form.password,
        phone:     form.phone.trim() || null,
      })
      login(data)          // log the user in immediately after registration
      setDone(true)
      setTimeout(() => navigate('/dashboard', { replace: true }), 1500)
    } catch (err) {
      const data = err.response?.data
      if (data && typeof data === 'object' && !data.message) {
        // Server returned field-level validation errors
        setErrors(data)
      } else {
        setApiErr(data?.message || 'Registration failed. Please try again.')
      }
    } finally {
      setLoading(false)
    }
  }

  /* ── Success state ── */
  if (done) {
    return (
      <div className="auth-shell">
        <BrandPanel />
        <div className="auth-form-panel">
          <div className="auth-form-inner" style={{ textAlign: 'center' }}>
            <div style={{
              width: 64, height: 64, borderRadius: '50%',
              background: 'var(--green)', display: 'inline-flex',
              alignItems: 'center', justifyContent: 'center', marginBottom: '1.25rem',
            }}>
              <svg viewBox="0 0 24 24" stroke="#fff" fill="none"
                   strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"
                   width="28" height="28">
                <polyline points="20 6 9 17 4 12"/>
              </svg>
            </div>
            <h1 className="form-title">Account created!</h1>
            <p className="form-desc">
              Welcome, {form.firstName}. Taking you to your dashboard…
            </p>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="auth-shell">
      <BrandPanel />

      <div className="auth-form-panel">
        <div className="auth-form-inner">
          <p className="form-eyebrow">Get started</p>
          <h1 className="form-title">Create your account</h1>
          <p className="form-desc">
            File and track institutional complaints — quick to set up.
          </p>

          {apiErr && (
            <div className="alert alert-error">
              <AlertIcon /> {apiErr}
            </div>
          )}

          <form onSubmit={submit} noValidate>

            {/* Name row */}
            <div className="field-row">
              <div className="field-group">
                <label htmlFor="firstName">First name</label>
                <div className="field-wrap">
                  <UserIcon />
                  <input
                    id="firstName" name="firstName" type="text"
                    className={`field-input${errors.firstName ? ' error-field' : ''}`}
                    placeholder="Ali"
                    value={form.firstName}
                    onChange={handle}
                    autoFocus
                    autoComplete="given-name"
                  />
                </div>
                {errors.firstName && (
                  <p className="field-err"><AlertIcon />{errors.firstName}</p>
                )}
              </div>

              <div className="field-group">
                <label htmlFor="lastName">Last name</label>
                <div className="field-wrap">
                  <UserIcon />
                  <input
                    id="lastName" name="lastName" type="text"
                    className={`field-input${errors.lastName ? ' error-field' : ''}`}
                    placeholder="Khan"
                    value={form.lastName}
                    onChange={handle}
                    autoComplete="family-name"
                  />
                </div>
                {errors.lastName && (
                  <p className="field-err"><AlertIcon />{errors.lastName}</p>
                )}
              </div>
            </div>

            {/* Email */}
            <div className="field-group">
              <label htmlFor="email">College email</label>
              <div className="field-wrap">
                <MailIcon />
                <input
                  id="email" name="email" type="email"
                  className={`field-input${errors.email ? ' error-field' : ''}`}
                  placeholder="ali@gecwyd.ac.in"
                  value={form.email}
                  onChange={handle}
                  autoComplete="email"
                />
              </div>
              {errors.email && <p className="field-err"><AlertIcon />{errors.email}</p>}
            </div>

            {/* Phone (optional) */}
            <div className="field-group">
              <label htmlFor="phone">
                Phone{' '}
                <span style={{ color: 'var(--slate-4)', fontWeight: 400 }}>
                  (optional)
                </span>
              </label>
              <div className="field-wrap">
                <PhoneIcon />
                <input
                  id="phone" name="phone" type="tel"
                  className="field-input"
                  placeholder="9876543210"
                  value={form.phone}
                  onChange={handle}
                  autoComplete="tel"
                />
              </div>
            </div>

            {/* Password */}
            <div className="field-group">
              <label htmlFor="password">Password</label>
              <div className="field-wrap">
                <LockIcon />
                <input
                  id="password" name="password"
                  type={showPw ? 'text' : 'password'}
                  className={`field-input${errors.password ? ' error-field' : ''}`}
                  placeholder="Min. 8 chars, 1 letter + 1 number"
                  value={form.password}
                  onChange={handle}
                  autoComplete="new-password"
                />
                <button type="button" className="field-toggle"
                  onClick={() => setShowPw(v => !v)}
                  title={showPw ? 'Hide' : 'Show'}>
                  <EyeIcon off={showPw} />
                </button>
              </div>
              {errors.password && (
                <p className="field-err"><AlertIcon />{errors.password}</p>
              )}

              {/* Strength meter — only renders once user starts typing */}
              {form.password && (
                <>
                  <div className="strength-bar-wrap">
                    {[1, 2, 3, 4].map(i => (
                      <div
                        key={i}
                        className={`strength-seg${
                          i <= strength.score
                            ? ` active-${STRENGTH_CLASSES[strength.score]}`
                            : ''
                        }`}
                      />
                    ))}
                  </div>
                  <p className="strength-label">
                    {strength.label && `Password strength: ${strength.label}`}
                  </p>
                </>
              )}
            </div>

            <button type="submit" className="btn-cta coral" disabled={loading}>
              {loading
                ? <><span className="btn-spinner" /> Creating account…</>
                : <><CheckIcon /> Create account</>
              }
            </button>
          </form>

          <p className="switch-link">
            Already have an account? <Link to="/login">Sign in →</Link>
          </p>
        </div>
      </div>
    </div>
  )
}
