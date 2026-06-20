import React, { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { complaintsApi } from '../api/complaints'
import Sidebar from '../components/Sidebar'
import { Navbar, PageHeader, Footer } from '../components/Layout'
import { StatusBadge, PriorityBadge, EmptyState, Spinner } from '../components/UI'

/*
  ComplaintList — the main complaints table.

  Admin:   sees all complaints, can filter by status, search by subject.
  Student: sees only their own complaints, same filter/search UX.

  MENTOR NOTE — local filter vs server-side filter:
  We load the full list once (complaintsApi.list()) then filter in-memory.
  This is fine for hundreds of complaints. When the dataset grows to
  thousands, switch to query params:  GET /api/complaints?status=SUBMITTED&q=wifi
  and the backend returns the filtered page. Keep the UX the same — just
  change what happens inside the useEffect.
*/

const STATUS_OPTIONS = ['ALL', 'SUBMITTED', 'IN_REVIEW', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'REJECTED']

export default function ComplaintList() {
  const { isAdmin } = useAuth()
  const admin = isAdmin()

  const [complaints, setComplaints] = useState([])
  const [filtered, setFiltered] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [search, setSearch] = useState('')

  // Load list once on mount
  useEffect(() => {
    complaintsApi.list()
      .then(data => {
        setComplaints(data)
        setFiltered(data)
      })
      .catch(() => setError('Failed to load complaints.'))
      .finally(() => setLoading(false))
  }, [])

  // Re-filter whenever search or status filter changes
  useEffect(() => {
    let result = [...complaints]
    if (statusFilter !== 'ALL') {
      result = result.filter(c => c.status === statusFilter)
    }
    if (search.trim()) {
      const q = search.toLowerCase()
      result = result.filter(c =>
        c.subject.toLowerCase().includes(q) ||
        c.category.toLowerCase().includes(q)
      )
    }
    setFiltered(result)
  }, [statusFilter, search, complaints])

  const handleDelete = async (id) => {
    if (!window.confirm('Soft-delete this complaint? It will be hidden but not permanently removed.')) return
    try {
      await complaintsApi.delete(id)
      setComplaints(prev => prev.filter(c => c.id !== id))
    } catch {
      alert('Failed to delete complaint.')
    }
  }

  return (
    <div className="shell">
      <Sidebar />
      <div className="shell-main">
        <Navbar
          title={admin ? 'All Complaints' : 'My Complaints'}
          actions={
            !admin && (
              <Link to="/complaints/new" className="btn btn-primary btn-sm">
                + New Complaint
              </Link>
            )
          }
        />

        <div className="page-content">
          <PageHeader
            title={admin ? 'All Complaints' : 'My Complaints'}
            subtitle={admin
              ? 'Review, assign, and resolve complaints from all students.'
              : 'Track every complaint you\'ve submitted.'}
            badge={filtered.length}
          // action={
          //   !admin && (
          //     <Link to="/complaints/new" className="btn btn-primary">
          //       + New Complaint
          //     </Link>
          //   )
          // }
          />

          {/* ── Toolbar: search + status filter ── */}
          <div className="toolbar">
            <input
              className="search-input"
              type="text"
              placeholder="Search by subject or category…"
              value={search}
              onChange={e => setSearch(e.target.value)}
            />
            <div className="filter-tabs">
              {STATUS_OPTIONS.map(s => (
                <button
                  key={s}
                  className={'filter-tab' + (statusFilter === s ? ' filter-tab-active' : '')}
                  onClick={() => setStatusFilter(s)}
                >
                  {s === 'ALL' ? 'All' : s.replace('_', ' ')}
                </button>
              ))}
            </div>
          </div>

          {loading && (
            <div style={{ display: 'flex', justifyContent: 'center', padding: '3rem' }}>
              <Spinner size={32} />
            </div>
          )}

          {error && <div className="alert alert-error">{error}</div>}

          {!loading && !error && filtered.length === 0 && (
            <EmptyState
              message={
                search || statusFilter !== 'ALL'
                  ? 'No complaints match your filters.'
                  : admin
                    ? 'No complaints have been filed yet.'
                    : 'You haven\'t filed any complaints yet.'
              }
              action={
                !admin && (
                  <Link to="/complaints/new" className="btn btn-primary btn-sm" style={{ marginTop: '1rem' }}>
                    File a complaint
                  </Link>
                )
              }
            />
          )}

          {!loading && !error && filtered.length > 0 && (
            <div className="table-container">
              <table className="complaint-table">
                <thead>
                  <tr>
                    <th>#</th>
                    <th>Subject</th>
                    <th>Category</th>
                    <th>Priority</th>
                    <th>Status</th>
                    {admin && <th>Filed by</th>}
                    <th>Submitted</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map(c => (
                    <tr key={c.id}>
                      <td className="td-id">#{c.id}</td>
                      <td className="td-subject">
                        <Link to={`/complaints/${c.id}`} className="link-subject">
                          {c.subject}
                        </Link>
                      </td>
                      <td>{c.category}</td>
                      <td><PriorityBadge priority={c.priority} /></td>
                      <td><StatusBadge status={c.status} /></td>
                      {admin && <td className="td-email">{c.submittedByEmail}</td>}
                      <td className="td-date">
                        {new Date(c.submittedAt).toLocaleDateString('en-IN', {
                          day: 'numeric', month: 'short', year: 'numeric'
                        })}
                      </td>
                      <td>
                        <div className="action-row">
                          <Link to={`/complaints/${c.id}`} className="btn-action">
                            View
                          </Link>
                          {/* Admin can delete any; student can only delete own DRAFT/SUBMITTED */}
                          {(admin || ['DRAFT', 'SUBMITTED'].includes(c.status)) && (
                            <button
                              className="btn-action btn-action-danger"
                              onClick={() => handleDelete(c.id)}
                            >
                              Delete
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        <Footer />
      </div>
    </div>
  )
}
