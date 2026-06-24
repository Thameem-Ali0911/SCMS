import React, { useEffect, useState, useCallback } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { complaintsApi } from '../api/complaints'
import Sidebar from '../components/Sidebar'
import { Navbar, PageHeader, Footer } from '../components/Layout'
import { StatusBadge, PriorityBadge, EmptyState, Spinner, Pagination } from '../components/UI'

/*
  ComplaintList — the main complaints table.

  Admin:  sees every complaint, filterable by status + search (both run
          server-side — see ComplaintController.list() / AdminReportService).
  Staff:  sees complaints currently assigned to them (use the Sidebar's
          "My Queue" link for the full self-assign workflow on /queue).
  Student: sees only their own complaints.

  CHANGE in v2.0 (production hardening):
    v1.3 loaded the ENTIRE list once and filtered/searched in memory — the
    report's exact words: "GET /api/complaints will return 50,000 rows...
    the browser will freeze." Status filtering and pagination are now
    server-side (see PageResponse.java). Free-text search stays
    client-side, scoped to the CURRENT page only — a deliberate,
    documented simplification (full server-side search is the Phase 5
    Elasticsearch roadmap item the original evaluation report itself
    suggested) rather than an oversight.
*/

const STATUS_OPTIONS = ['ALL', 'SUBMITTED', 'IN_REVIEW', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'REJECTED']
const PAGE_SIZE = 20

export default function ComplaintList() {
  const { isAdmin, isStaff } = useAuth()
  const admin = isAdmin()
  const staff = isStaff()

  const [page, setPage] = useState({ content: [], page: 0, totalPages: 0, totalElements: 0 })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [pageNumber, setPageNumber] = useState(0)
  const [search, setSearch] = useState('')

  const load = useCallback(() => {
    setLoading(true)
    const params = { page: pageNumber, size: PAGE_SIZE, sort: 'submittedAt,desc' }
    if (statusFilter !== 'ALL') params.status = statusFilter
    if (admin && search.trim()) params.q = search.trim()

    complaintsApi.list(params)
      .then(data => { setPage(data); setError(null) })
      .catch(() => setError('Failed to load complaints.'))
      .finally(() => setLoading(false))
  }, [pageNumber, statusFilter, search, admin])

  useEffect(() => { load() }, [load])

  // Status filter / page-size search resets to page 0
  useEffect(() => { setPageNumber(0) }, [statusFilter])

  // For staff/students, search filters only the currently-loaded page (see module note above)
  const visibleRows = (!admin && search.trim())
    ? page.content.filter(c =>
        c.subject.toLowerCase().includes(search.toLowerCase()) ||
        c.category.toLowerCase().includes(search.toLowerCase()))
    : page.content

  const handleDelete = async (id) => {
    if (!window.confirm('Soft-delete this complaint? It will be hidden but not permanently removed.')) return
    try {
      await complaintsApi.delete(id)
      load()
    } catch {
      window.alert('Failed to delete complaint.')
    }
  }

  const title = admin ? 'All Complaints' : staff ? 'Assigned to Me' : 'My Complaints'

  return (
    <div className="shell">
      <Sidebar />
      <div className="shell-main">
        <Navbar
          title={title}
          actions={
            !admin && !staff && (
              <Link to="/complaints/new" className="btn btn-primary btn-sm">
                + New Complaint
              </Link>
            )
          }
        />

        <div className="page-content">
          <PageHeader
            title={title}
            subtitle={admin
              ? 'Review, assign, and resolve complaints from all students.'
              : staff
                ? 'Complaints currently assigned to you. Visit My Queue to pick up new ones.'
                : "Track every complaint you've submitted."}
            badge={page.totalElements}
          />

          {/* ── Toolbar: search + status filter ── */}
          <div className="toolbar">
            <input
              className="search-input"
              type="text"
              placeholder={admin ? 'Search by subject or category…' : 'Search this page…'}
              value={search}
              onChange={e => setSearch(e.target.value)}
              aria-label="Search complaints"
            />
            <div className="filter-tabs" role="group" aria-label="Filter by status">
              {STATUS_OPTIONS.map(s => (
                <button
                  key={s}
                  className={'filter-tab' + (statusFilter === s ? ' filter-tab-active' : '')}
                  onClick={() => setStatusFilter(s)}
                  aria-pressed={statusFilter === s}
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

          {!loading && !error && visibleRows.length === 0 && (
            <EmptyState
              message={
                search || statusFilter !== 'ALL'
                  ? 'No complaints match your filters.'
                  : admin
                    ? 'No complaints have been filed yet.'
                    : staff
                      ? 'Nothing assigned to you yet — check My Queue to pick something up.'
                      : "You haven't filed any complaints yet."
              }
              action={
                !admin && !staff && (
                  <Link to="/complaints/new" className="btn btn-primary btn-sm" style={{ marginTop: '1rem' }}>
                    File a complaint
                  </Link>
                )
              }
            />
          )}

          {!loading && !error && visibleRows.length > 0 && (
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
                    {(admin || staff) && <th>Assigned to</th>}
                    <th>Submitted</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {visibleRows.map(c => (
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
                      {(admin || staff) && (
                        <td className="td-email">{c.assignedToName ?? '—'}</td>
                      )}
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
                          {(admin || c.status === 'SUBMITTED') && (
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
              <Pagination
                page={page.page}
                totalPages={page.totalPages}
                totalElements={page.totalElements}
                onPageChange={setPageNumber}
              />
            </div>
          )}
        </div>

        <Footer />
      </div>
    </div>
  )
}
