import React, { useEffect, useState, useCallback } from 'react'
import { useParams, Link, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { complaintsApi } from '../api/complaints'
import { adminApi } from '../api/admin'
import Sidebar from '../components/Sidebar'
import { Navbar, PageHeader, Footer } from '../components/Layout'
import { StatusBadge, PriorityBadge, Spinner } from '../components/UI'

/*
  ComplaintDetail — full complaint view for every role.

  CHANGE in v2.0 (production hardening):

    • The "Update Status" panel is no longer admin-only — it's shown to
      whichever STAFF user this complaint is actually assigned to as well
      (mirroring ComplaintService.assertCanManage's server-side rule: an
      assignee or an admin can change status, nobody else). v1.3 gated this
      purely on isAdmin(), which meant a STAFF role — had it existed in the
      UI at all — would never have been able to do the one thing it exists
      to do.

    • Status options shown to a non-admin assignee are restricted to the
      LEGAL next states (mirroring ComplaintStatusPolicy.java) so a staff
      member can't even attempt an illegal transition; an admin sees every
      status, matching their server-side override authority.

    • "Assign to me" lets a STAFF/ADMIN claim an unassigned complaint right
      from this page, not just from the /queue list.

    • Admin gets a Reassign control, and everyone who can view the
      complaint now gets an Audit Trail panel — exposing the
      ComplaintVersion history that v1.3 built but never put behind any
      API at all (the v1.3 report's exact words: "the audit log is never
      queried through any API").
*/

const ALL_STATUSES = ['SUBMITTED', 'IN_REVIEW', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'REJECTED']

// Mirrors backend ComplaintStatusPolicy — kept here only to drive which
// options a non-admin assignee sees; the backend remains the actual source
// of truth and will reject anything illegal regardless of what the UI offers.
const NEXT_ALLOWED = {
  SUBMITTED:   ['IN_REVIEW', 'REJECTED'],
  IN_REVIEW:   ['IN_PROGRESS', 'REJECTED'],
  IN_PROGRESS: ['RESOLVED', 'REJECTED'],
  RESOLVED:    ['CLOSED'],
  REJECTED:    ['CLOSED'],
  CLOSED:      [],
}

function formatDate(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('en-IN', {
    day: 'numeric', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit'
  })
}

export default function ComplaintDetail() {
  const { id } = useParams()
  const { user, isAdmin, isStaff } = useAuth()
  const location = useLocation()
  const admin = isAdmin()
  const staff = isStaff()

  const [complaint, setComplaint] = useState(null)
  const [history,   setHistory]   = useState(null)
  const [loading,   setLoading]   = useState(true)
  const [error,     setError]     = useState(null)
  const [flash,     setFlash]     = useState(location.state?.flash ?? null)

  const [newStatus, setNewStatus]   = useState('')
  const [reason,    setReason]      = useState('')
  const [updating,  setUpdating]    = useState(false)
  const [updateErr, setUpdateErr]   = useState(null)

  const [assigningMe, setAssigningMe] = useState(false)
  const [workload, setWorkload]       = useState([])
  const [reassignTo, setReassignTo]   = useState('')
  const [reassigning, setReassigning] = useState(false)

  const isAssignee = complaint && user && complaint.assignedToId === user.userId
  const canManageStatus = admin || isAssignee
  const statusOptions = admin ? ALL_STATUSES : (NEXT_ALLOWED[complaint?.status] ?? [])

  const load = useCallback(() => {
    setLoading(true)
    Promise.all([
      complaintsApi.get(id),
      complaintsApi.history(id).catch(() => null), // history is a nice-to-have; don't fail the page if it 403s
    ])
      .then(([data, hist]) => {
        setComplaint(data)
        setNewStatus(data.status)
        setHistory(hist)
        setError(null)
      })
      .catch(err => {
        setError(
          err.response?.status === 404
            ? "Complaint not found or you don't have access to it."
            : 'Failed to load complaint.'
        )
      })
      .finally(() => setLoading(false))
  }, [id])

  useEffect(() => { load() }, [load])

  useEffect(() => {
    if (admin) {
      adminApi.staffWorkload().then(setWorkload).catch(() => {})
    }
  }, [admin])

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
      load() // refresh history too
    } catch (err) {
      setUpdateErr(err.response?.data?.message ?? 'Update failed.')
    } finally {
      setUpdating(false)
    }
  }

  const handleAssignToMe = async () => {
    setAssigningMe(true)
    try {
      const updated = await complaintsApi.selfAssign(id)
      setComplaint(updated)
      setNewStatus(updated.status)
      setFlash('Complaint assigned to you.')
      load()
    } catch (err) {
      setUpdateErr(err.response?.data?.message ?? 'Failed to assign complaint.')
    } finally {
      setAssigningMe(false)
    }
  }

  const handleReassign = async () => {
    if (!reassignTo) return
    setReassigning(true)
    try {
      const updated = await complaintsApi.assign(id, Number(reassignTo))
      setComplaint(updated)
      setFlash(`Reassigned to ${updated.assignedToName}.`)
      setReassignTo('')
    } catch (err) {
      setUpdateErr(err.response?.data?.message ?? 'Failed to reassign complaint.')
    } finally {
      setReassigning(false)
    }
  }

  return (
    <div className="shell">
      <Sidebar />
      <div className="shell-main">
        <Navbar title={complaint ? `Complaint #${complaint.id}` : 'Complaint'} />

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

                  <div className="detail-meta-row">
                    <StatusBadge   status={complaint.status}   />
                    <PriorityBadge priority={complaint.priority} />
                    <span className="detail-category">{complaint.category}</span>
                    {complaint.assignedToName ? (
                      <span className="detail-assigned">Assigned to {complaint.assignedToName}</span>
                    ) : (staff || admin) && (
                      <button
                        className="btn btn-sm btn-secondary"
                        onClick={handleAssignToMe}
                        disabled={assigningMe}
                      >
                        {assigningMe ? 'Assigning…' : 'Assign to me'}
                      </button>
                    )}
                  </div>

                  <div className="detail-card">
                    <h3 className="detail-card-title">Description</h3>
                    <p className="detail-description">{complaint.description}</p>
                  </div>

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

                  {/* ── Audit trail — new in v2.0 ── */}
                  {history && history.versions?.length > 0 && (
                    <div className="detail-card">
                      <h3 className="detail-card-title">Audit Trail</h3>
                      <div className="timeline">
                        {history.versions.map(v => (
                          <div className="timeline-item" key={v.versionNumber}>
                            <span className="timeline-label">
                              v{v.versionNumber} · {v.changeType?.replace('_', ' ')}
                              {v.previousStatus && v.newStatus && v.previousStatus !== v.newStatus && (
                                <> ({v.previousStatus} → {v.newStatus})</>
                              )}
                              {v.changeReason && <><br /><em>{v.changeReason}</em></>}
                            </span>
                            <span className="timeline-value">
                              {v.changedByName}<br />{formatDate(v.changedAt)}
                            </span>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                </div>

                {/* ── RIGHT COLUMN: action panels ── */}
                {(canManageStatus || admin) && (
                  <div className="detail-sidebar">

                    {canManageStatus && statusOptions.length > 0 && (
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
                              <option value={complaint.status}>{complaint.status.replace('_', ' ')} (current)</option>
                              {statusOptions.map(s => (
                                <option key={s} value={s}>{s.replace('_', ' ')}</option>
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
                    )}

                    {admin && (
                      <div className="detail-card">
                        <h3 className="detail-card-title">Reassign</h3>
                        <div className="field">
                          <select
                            className="field-input"
                            value={reassignTo}
                            onChange={e => setReassignTo(e.target.value)}
                          >
                            <option value="">Choose staff…</option>
                            {workload.map(w => (
                              <option key={w.staffId} value={w.staffId}>
                                {w.staffName} ({w.assignedOpen} open)
                              </option>
                            ))}
                          </select>
                        </div>
                        <button
                          className="btn btn-secondary"
                          style={{ width: '100%' }}
                          disabled={reassigning || !reassignTo}
                          onClick={handleReassign}
                        >
                          {reassigning ? 'Reassigning…' : 'Reassign'}
                        </button>
                      </div>
                    )}

                    <div className="detail-card">
                      <h3 className="detail-card-title">Filed by</h3>
                      <p className="detail-meta-value">{complaint.submittedByName}</p>
                      <p className="detail-meta-sub">{complaint.submittedByEmail}</p>
                    </div>

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
