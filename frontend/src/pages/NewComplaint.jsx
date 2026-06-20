import React, { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { complaintsApi } from '../api/complaints'
import Sidebar from '../components/Sidebar'
import { Navbar, PageHeader, Footer } from '../components/Layout'

/*
  NewComplaint — form for students to file a new complaint.

  MENTOR NOTE — controlled form pattern in React:
  Every input is a "controlled component" — its value is stored in React state,
  and every keystroke calls setState via the onChange handler. This means:
    1. React state is always the single source of truth for the form
    2. We can validate the state before submission
    3. We can clear the form after success (just reset the state object)
  The alternative (uncontrolled with refs) is messier for multi-field forms.

  MENTOR NOTE — client-side validation vs server-side validation:
  We validate here for fast UX feedback (no network round-trip).
  The backend ALSO validates (via @Valid + @NotBlank etc) as the real gate.
  Both layers are necessary — client-side is UX, server-side is security.
*/

const CATEGORIES = [
  'IT & Infrastructure',
  'Academics',
  'Hostel & Facilities',
  'Library',
  'Transport',
  'Administration',
  'Canteen',
  'Other',
]

const PRIORITIES = [
  { value: 'LOW',      label: 'Low — minor inconvenience' },
  { value: 'MEDIUM',   label: 'Medium — affects my work' },
  { value: 'HIGH',     label: 'High — significant disruption' },
  { value: 'CRITICAL', label: 'Critical — urgent, needs immediate attention' },
]

const INIT = { subject: '', description: '', category: '', priority: 'MEDIUM' }

export default function NewComplaint() {
  const navigate = useNavigate()
  const [form,       setForm]      = useState(INIT)
  const [errors,     setErrors]    = useState({})
  const [submitting, setSubmitting] = useState(false)
  const [serverError, setServerError] = useState(null)

  const set = (field) => (e) =>
    setForm(prev => ({ ...prev, [field]: e.target.value }))

  const validate = () => {
    const e = {}
    if (!form.subject.trim() || form.subject.trim().length < 5)
      e.subject = 'Subject must be at least 5 characters.'
    if (!form.description.trim() || form.description.trim().length < 20)
      e.description = 'Description must be at least 20 characters.'
    if (!form.category)
      e.category = 'Please select a category.'
    return e
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    const errs = validate()
    if (Object.keys(errs).length > 0) {
      setErrors(errs)
      return
    }

    setSubmitting(true)
    setServerError(null)
    try {
      const created = await complaintsApi.create(form)
      // Redirect to the new complaint's detail page
      navigate(`/complaints/${created.id}`, {
        state: { flash: 'Complaint submitted successfully.' }
      })
    } catch (err) {
      setServerError(
        err.response?.data?.message ?? 'Submission failed. Please try again.'
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="shell">
      <Sidebar />
      <div className="shell-main">
        <Navbar title="New Complaint" />

        <div className="page-content page-content-narrow">
          <PageHeader
            title="File a Complaint"
            subtitle="Describe the issue clearly so it can be routed to the right team."
            action={
              <Link to="/complaints" className="btn btn-ghost">
                ← Back to list
              </Link>
            }
          />

          {serverError && (
            <div className="alert alert-error" style={{ marginBottom: '1.5rem' }}>
              {serverError}
            </div>
          )}

          <div className="form-card">
            <form onSubmit={handleSubmit} noValidate>

              {/* Subject */}
              <div className="field">
                <label className="field-label" htmlFor="subject">
                  Subject <span className="required">*</span>
                </label>
                <input
                  id="subject"
                  type="text"
                  className={'field-input' + (errors.subject ? ' field-input-error' : '')}
                  placeholder="e.g. Wi-Fi outage in CS Lab B"
                  value={form.subject}
                  onChange={set('subject')}
                  maxLength={255}
                />
                {errors.subject && <p className="field-error">{errors.subject}</p>}
                <p className="field-hint">{form.subject.length}/255</p>
              </div>

              {/* Category + Priority row */}
              <div className="field-row">
                <div className="field field-half">
                  <label className="field-label" htmlFor="category">
                    Category <span className="required">*</span>
                  </label>
                  <select
                    id="category"
                    className={'field-input' + (errors.category ? ' field-input-error' : '')}
                    value={form.category}
                    onChange={set('category')}
                  >
                    <option value="">Select category…</option>
                    {CATEGORIES.map(c => (
                      <option key={c} value={c}>{c}</option>
                    ))}
                  </select>
                  {errors.category && <p className="field-error">{errors.category}</p>}
                </div>

                <div className="field field-half">
                  <label className="field-label" htmlFor="priority">Priority</label>
                  <select
                    id="priority"
                    className="field-input"
                    value={form.priority}
                    onChange={set('priority')}
                  >
                    {PRIORITIES.map(p => (
                      <option key={p.value} value={p.value}>{p.label}</option>
                    ))}
                  </select>
                </div>
              </div>

              {/* Description */}
              <div className="field">
                <label className="field-label" htmlFor="description">
                  Description <span className="required">*</span>
                </label>
                <textarea
                  id="description"
                  className={'field-input field-textarea' + (errors.description ? ' field-input-error' : '')}
                  placeholder="Describe the issue in detail. Include location, time, and any steps you've already taken to resolve it."
                  value={form.description}
                  onChange={set('description')}
                  rows={6}
                />
                {errors.description && <p className="field-error">{errors.description}</p>}
                <p className="field-hint">
                  {form.description.length} chars (minimum 20)
                </p>
              </div>

              {/* Actions */}
              <div className="form-actions">
                <Link to="/complaints" className="btn btn-ghost">
                  Cancel
                </Link>
                <button
                  type="submit"
                  className="btn btn-primary"
                  disabled={submitting}
                >
                  {submitting ? 'Submitting…' : 'Submit Complaint'}
                </button>
              </div>
            </form>
          </div>
        </div>

        <Footer />
      </div>
    </div>
  )
}
