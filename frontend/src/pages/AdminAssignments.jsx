import React, { useEffect, useState, useCallback } from 'react'
import { Link } from 'react-router-dom'
import { complaintsApi } from '../api/complaints'
import { adminApi } from '../api/admin'
import { useToast } from '../components/Toast'
import Sidebar from '../components/Sidebar'
import { Navbar, PageHeader, Footer } from '../components/Layout'
import { StatusBadge, PriorityBadge, Spinner, EmptyState, Pagination } from '../components/UI'

/*
  AdminAssignments — lets an admin assign unassigned complaints to a
  specific STAFF (or ADMIN) user, with each candidate's current workload
  visible alongside the queue.

  This is new in v2.0 — it's the direct answer to "add an additional
  entity like admin, who can get complaints assigned to them so they can
  resolve it": STAFF users can already self-assign from /queue, and this
  page gives an admin the complementary top-down assignment workflow —
  picking who should take a given complaint, informed by who is least
  loaded right now (adminApi.staffWorkload() is pre-sorted lightest-load-first
  by AdminAssignmentService).
*/

const PAGE_SIZE = 20

export default function AdminAssignments() {
  const toast = useToast()

  const [queue, setQueue] = useState({ content: [], page: 0, totalPages: 0, totalElements: 0 })
  const [workload, setWorkload] = useState([])
  const [pageNumber, setPageNumber] = useState(0)
  const [loading, setLoading] = useState(true)
  const [assigning, setAssigning] = useState({}) // { [complaintId]: true }
  const [selected, setSelected] = useState({})   // { [complaintId]: staffId }

  const load = useCallback(() => {
    setLoading(true)
    Promise.all([
      complaintsApi.queueUnassigned({ page: pageNumber, size: PAGE_SIZE }),
      adminApi.staffWorkload(),
    ])
      .then(([q, w]) => { setQueue(q); setWorkload(w) })
      .catch(() => toast.error('Failed to load assignment data.'))
      .finally(() => setLoading(false))
  }, [pageNumber])

  useEffect(() => { load() }, [load])

  const handleAssign = async (complaintId) => {
    const staffId = selected[complaintId]
    if (!staffId) {
      toast.error('Choose a staff member first.')
      return
    }
    setAssigning(prev => ({ ...prev, [complaintId]: true }))
    try {
      await complaintsApi.assign(complaintId, Number(staffId))
      const staffName = workload.find(w => w.staffId === Number(staffId))?.staffName ?? 'staff'
      toast.success(`Complaint #${complaintId} assigned to ${staffName}.`)
      load()
    } catch (err) {
      toast.error(err.response?.data?.message ?? 'Failed to assign complaint.')
    } finally {
      setAssigning(prev => ({ ...prev, [complaintId]: false }))
    }
  }

  return (
    <div className="shell">
      <Sidebar />
      <div className="shell-main">
        <Navbar title="Assignments" />

        <div className="page-content">
          <PageHeader
            title="Assignments"
            subtitle="Assign unassigned complaints to a staff member, informed by their current workload."
            badge={queue.totalElements}
          />

          {loading && (
            <div style={{ display: 'flex', justifyContent: 'center', padding: '4rem' }}>
              <Spinner size={32} />
            </div>
          )}

          {!loading && (
            <div className="detail-layout">
              <div className="detail-main">
                {queue.content.length === 0 ? (
                  <EmptyState message="Nothing waiting to be assigned right now." />
                ) : (
                  <div className="table-container">
                    <table className="complaint-table">
                      <thead>
                        <tr>
                          <th>#</th>
                          <th>Subject</th>
                          <th>Priority</th>
                          <th>Status</th>
                          <th>Submitted</th>
                          <th>Assign to</th>
                        </tr>
                      </thead>
                      <tbody>
                        {queue.content.map(c => (
                          <tr key={c.id}>
                            <td className="td-id">#{c.id}</td>
                            <td className="td-subject">
                              <Link to={`/complaints/${c.id}`} className="link-subject">{c.subject}</Link>
                            </td>
                            <td><PriorityBadge priority={c.priority} /></td>
                            <td><StatusBadge status={c.status} /></td>
                            <td className="td-date">
                              {new Date(c.submittedAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })}
                            </td>
                            <td>
                              <div className="action-row">
                                <select
                                  className="role-select"
                                  value={selected[c.id] ?? ''}
                                  onChange={(e) => setSelected(prev => ({ ...prev, [c.id]: e.target.value }))}
                                  aria-label={`Assign complaint #${c.id} to`}
                                >
                                  <option value="" disabled>Choose staff…</option>
                                  {workload.map(w => (
                                    <option key={w.staffId} value={w.staffId}>
                                      {w.staffName} ({w.assignedOpen} open)
                                    </option>
                                  ))}
                                </select>
                                <button
                                  className="btn-action"
                                  style={{ color: 'var(--indigo)', borderColor: 'var(--indigo)' }}
                                  disabled={!!assigning[c.id]}
                                  onClick={() => handleAssign(c.id)}
                                >
                                  {assigning[c.id] ? '…' : 'Assign'}
                                </button>
                              </div>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                    <Pagination
                      page={queue.page}
                      totalPages={queue.totalPages}
                      totalElements={queue.totalElements}
                      onPageChange={setPageNumber}
                    />
                  </div>
                )}
              </div>

              <div className="detail-sidebar">
                <div className="detail-card">
                  <h3 className="detail-card-title">Staff Workload</h3>
                  {workload.length === 0 ? (
                    <p className="empty-inline" style={{ padding: 0 }}>No STAFF or ADMIN users yet.</p>
                  ) : (
                    <div>
                      {workload.map(w => (
                        <div key={w.staffId} className="workload-card">
                          <div>
                            <div className="workload-name">{w.staffName}</div>
                            <div className="workload-email">{w.email}</div>
                          </div>
                          <div className="workload-counts">
                            <span className="badge badge-amber">{w.assignedOpen} open</span>
                            <span className="badge badge-green">{w.resolvedTotal} resolved</span>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            </div>
          )}
        </div>

        <Footer />
      </div>
    </div>
  )
}
