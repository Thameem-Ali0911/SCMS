import React, { useEffect, useState } from 'react'
import { useParams, Link, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { complaintsApi } from '../api/complaints'
import Sidebar from '../components/Sidebar'
import { Navbar, PageHeader, Footer } from '../components/Layout'
import { StatusBadge, PriorityBadge, Spinner } from '../components/UI'

/*
  ComplaintDetail — full complaint view for both roles.

  Student sees:
    - All complaint fields (read-only)
    - Status history (when implemented)

  Admin sees everything above PLUS:
    - Status update panel (dropdown + reason textarea)
    - Assigned to field
*/

const STATUSES = [
  'SUBMITTED', 'IN_REVIEW', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'REJECTED'
]

function formatDate(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('en-IN', {
    day: 'numeric', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit'
  })
}

export default function ComplaintDetail() {
  const { id } = useParams()
  const { isAdmin } = useAuth()
  const location = useLocation()
  const admin = isAdmin()

  const [complaint, setComplaint] = useState(null)
  const [loading,   setLoading]   = useState(true)
  const [error,     setError]     = useState(null)
  const [flash,     setFlash]     = useState(location.state?.flash ?? null)

  // Admin status update form state
  const [newStatus, setNewStatus]   = useState('')
  const [reason,    setReason]      = useState('')
  const [updating,  setUpdating]    = useState(false)
  const [updateErr, setUpdateErr]   = useState(null)

  useEffect(() => {
    complaintsApi.get(id)
      .then(data => {
        setComplaint(data)
        setNewStatus(data.status)
      })
      .catch(err => {
        setError(
          err.response?.status === 404
            ? 'Complaint not found or you don\'t have access to it.'
            : 'Failed to load complaint.'
        )
      })
      .finally(() => setLoading(false))
  }, [id])

  // Auto-clear flash message after 4 seconds
  useEffect(() => {
    if (!flash) return
    const t = setTimeout(() => setFlash(null), 4000)
    return () => clearTimeout(t)
  }, [flash])

  const handleUpdateStatus = async (e) => {
    e.preventDefault()
    if (!newStatus) return

    setUpdating(true)
    setUpdateErr(null)
    try {
      const updated = await complaintsApi.updateStatus(id, {
        status: newStatus,
        reason: reason.trim() || null,
      })
      setComplaint(updated)
      setFlash(`Status updated to ${updated.status.replace('_', ' ')}.`)
      setReason('')
    } catch (err) {
      setUpdateErr(err.response?.data?.message ?? 'Update failed.')
    } finally {
      setUpdating(false)
    }
  }

  return (
    <div className="shell">
      <Sidebar />
      <div className="shell-main">
        <Navbar
          title={complaint ? `Complaint #${complaint.id}` : 'Complaint'}
        />

        <div className="page-content">
          {loading && (
            <div style={{ display: 'flex', justifyContent: 'center', padding: '4rem' }}>
              <Spinner size={32} />
            </div>
          )}

          {error && <div className="alert alert-error">{error}</div>}

          {flash && <div className="alert alert-success">{flash}</div>}

          {!loading && !error && complaint && (
            <>
              <PageHeader
                title={complaint.subject}
                subtitle={`Filed by ${complaint.submittedByName} · ${formatDate(complaint.submittedAt)}`}
                action={
                  <Link to="/complaints" className="btn btn-ghost">
                    ← Back
                  </Link>
                }
              />

              <div className="detail-layout">

                {/* ── LEFT COLUMN: complaint body ── */}
                <div className="detail-main">

                  {/* Meta pills */}
                  <div className="detail-meta-row">
                    <StatusBadge   status={complaint.status}   />
                    <PriorityBadge priority={complaint.priority} />
                    <span className="detail-category">{complaint.category}</span>
                    {complaint.assignedToName && (
                      <span className="detail-assigned">
                        Assigned to {complaint.assignedToName}
                      </span>
                    )}
                  </div>

                  {/* Description */}
                  <div className="detail-card">
                    <h3 className="detail-card-title">Description</h3>
                    <p className="detail-description">{complaint.description}</p>
                  </div>

                  {/* Timeline — dates */}
                  <div className="detail-card">
                    <h3 className="detail-card-title">Timeline</h3>
                    <div className="timeline">
                      <div className="timeline-item">
                        <span className="timeline-label">Submitted</span>
                        <span className="timeline-value">{formatDate(complaint.submittedAt)}</span>
                      </div>
                      <div className="timeline-item">
                        <span className="timeline-label">Last updated</span>
                        <span className="timeline-value">{formatDate(complaint.updatedAt)}</span>
                      </div>
                      {complaint.resolvedAt && (
                        <div className="timeline-item">
                          <span className="timeline-label">Resolved</span>
                          <span className="timeline-value">{formatDate(complaint.resolvedAt)}</span>
                        </div>
                      )}
                    </div>
                  </div>

                </div>

                {/* ── RIGHT COLUMN: admin actions panel ── */}
                {admin && (
                  <div className="detail-sidebar">
                    <div className="detail-card">
                      <h3 className="detail-card-title">Update Status</h3>

                      {updateErr && (
                        <div className="alert alert-error" style={{ marginBottom: '1rem' }}>
                          {updateErr}
                        </div>
                      )}

                      <form onSubmit={handleUpdateStatus}>
                        <div className="field">
                          <label className="field-label" htmlFor="newStatus">Status</label>
                          <select
                            id="newStatus"
                            className="field-input"
                            value={newStatus}
                            onChange={e => setNewStatus(e.target.value)}
                          >
                            {STATUSES.map(s => (
                              <option key={s} value={s}>
                                {s.replace('_', ' ')}
                              </option>
                            ))}
                          </select>
                        </div>

                        <div className="field">
                          <label className="field-label" htmlFor="reason">
                            Note / Reason <span className="field-optional">(optional)</span>
                          </label>
                          <textarea
                            id="reason"
                            className="field-input field-textarea"
                            placeholder="e.g. Forwarded to IT department…"
                            value={reason}
                            onChange={e => setReason(e.target.value)}
                            rows={3}
                          />
                        </div>

                        <button
                          type="submit"
                          className="btn btn-primary"
                          disabled={updating || newStatus === complaint.status}
                          style={{ width: '100%' }}
                        >
                          {updating ? 'Saving…' : 'Save Status'}
                        </button>
                      </form>
                    </div>

                    {/* Filed-by info panel */}
                    <div className="detail-card">
                      <h3 className="detail-card-title">Filed by</h3>
                      <p className="detail-meta-value">{complaint.submittedByName}</p>
                      <p className="detail-meta-sub">{complaint.submittedByEmail}</p>
                    </div>

                    {/* Version info */}
                    <div className="detail-card">
                      <h3 className="detail-card-title">Version</h3>
                      <p className="detail-meta-value">v{complaint.version}</p>
                      <p className="detail-meta-sub">Optimistic lock version</p>
                    </div>
                  </div>
                )}

              </div>
            </>
          )}
        </div>

        <Footer />
      </div>
    </div>
  )
}
