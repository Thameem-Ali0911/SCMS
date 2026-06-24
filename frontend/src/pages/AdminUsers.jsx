import React, { useEffect, useState, useCallback } from 'react'
import { useAuth } from '../context/AuthContext'
import { adminApi } from '../api/admin'
import Sidebar from '../components/Sidebar'
import { Navbar, PageHeader, Footer } from '../components/Layout'
import { Spinner, Pagination } from '../components/UI'

/*
  AdminUsers — full user management page for admins.

  CHANGE in v2.0 (production hardening):
    • Role control is now a 3-way <select> (USER / STAFF / ADMIN) instead of
      a binary Promote/Demote toggle button — v1.3 could only ever assign
      USER or ADMIN, with no way to make someone STAFF from the UI at all.
    • Search is now sent to the backend (adminApi.listUsers({ q, page,
      size })) and the list itself is paginated — v1.3 loaded every user in
      the system on every page view and filtered entirely client-side.
    • File now has consistent LF line endings (v1.3's copy had Windows CRLF
      endings mixed into an otherwise-LF codebase — a Team Readiness
      finding in the evaluation report: "the first team conflict").
*/

function RoleBadge({ roles }) {
  const role = roles?.includes('ADMIN') ? 'ADMIN' : roles?.includes('STAFF') ? 'STAFF' : 'USER'
  const cls = role === 'ADMIN' ? 'badge-indigo' : role === 'STAFF' ? 'badge-amber' : 'badge-slate'
  const label = role === 'ADMIN' ? 'Admin' : role === 'STAFF' ? 'Staff' : 'User'
  return <span className={`badge ${cls}`}>{label}</span>
}

function StatusDot({ active }) {
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: '0.35rem' }}>
      <span style={{
        display: 'inline-block',
        width: 8, height: 8,
        borderRadius: '50%',
        background: active ? 'var(--green-9)' : 'var(--slate-5)',
      }} />
      <span style={{ color: active ? 'var(--green-9)' : 'var(--slate-5)', fontSize: '0.8rem' }}>
        {active ? 'Active' : 'Inactive'}
      </span>
    </span>
  )
}

const PAGE_SIZE = 20

export default function AdminUsers() {
  const { user: self } = useAuth()

  const [page, setPage] = useState({ content: [], page: 0, totalPages: 0, totalElements: 0 })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [flash, setFlash] = useState(null)
  const [flashType, setFlashType] = useState('success')

  const [search, setSearch] = useState('')
  const [pageNumber, setPageNumber] = useState(0)

  // Role/status filters apply to the current page only — a documented
  // simplification, same as ComplaintList's free-text search.
  const [roleFilter, setRoleFilter] = useState('ALL')
  const [statusFilter, setStatusFilter] = useState('ALL')

  const [actionLoading, setActionLoading] = useState({})

  const loadUsers = useCallback(() => {
    setLoading(true)
    adminApi.listUsers({ q: search.trim() || undefined, page: pageNumber, size: PAGE_SIZE })
      .then(data => { setPage(data); setError(null) })
      .catch(() => setError('Failed to load users. Is the backend running?'))
      .finally(() => setLoading(false))
  }, [search, pageNumber])

  useEffect(() => { loadUsers() }, [loadUsers])
  useEffect(() => { setPageNumber(0) }, [search])

  let filtered = page.content
  if (roleFilter !== 'ALL') {
    filtered = filtered.filter(u => (u.roles?.includes('ADMIN') ? 'ADMIN' : u.roles?.includes('STAFF') ? 'STAFF' : 'USER') === roleFilter)
  }
  if (statusFilter !== 'ALL') {
    filtered = filtered.filter(u => (statusFilter === 'ACTIVE' ? u.active : !u.active))
  }

  const showFlash = (msg, type = 'success') => {
    setFlash(msg)
    setFlashType(type)
    setTimeout(() => setFlash(null), 4000)
  }

  const setRowLoading = (id, val) =>
    setActionLoading(prev => ({ ...prev, [id]: val }))

  const replaceUser = (updated) =>
    setPage(prev => ({ ...prev, content: prev.content.map(x => x.id === updated.id ? updated : x) }))

  const handleToggleStatus = async (u) => {
    if (u.id === self?.userId) return
    const newActive = !u.active
    const label = newActive ? 'activated' : 'deactivated'
    setRowLoading(u.id, true)
    try {
      const updated = await adminApi.toggleUserStatus(u.id, newActive)
      replaceUser(updated)
      showFlash(`${updated.firstName} ${updated.lastName} ${label}.`)
    } catch (err) {
      showFlash(err.response?.data?.message ?? `Failed to ${label} user.`, 'error')
    } finally {
      setRowLoading(u.id, false)
    }
  }

  const handleChangeRole = async (u, newRole) => {
    if (u.id === self?.userId || newRole === currentRole(u)) return
    if (!window.confirm(`Change ${u.firstName} ${u.lastName}'s role to ${newRole}?`)) return
    setRowLoading(u.id, true)
    try {
      const updated = await adminApi.changeUserRole(u.id, newRole)
      replaceUser(updated)
      showFlash(`${updated.firstName} ${updated.lastName} is now ${newRole}.`)
    } catch (err) {
      showFlash(err.response?.data?.message ?? 'Failed to change role.', 'error')
    } finally {
      setRowLoading(u.id, false)
    }
  }

  const currentRole = (u) => u.roles?.includes('ADMIN') ? 'ADMIN' : u.roles?.includes('STAFF') ? 'STAFF' : 'USER'
  const isSelf = (u) => u.id === self?.userId

  return (
    <div className="shell">
      <Sidebar />
      <div className="shell-main">
        <Navbar title="User Management" />

        <div className="page-content">
          <PageHeader
            title="Users"
            subtitle="Manage all registered accounts — roles, status, and complaint activity."
            badge={page.totalElements}
          />

          {flash && (
            <div className={`alert alert-${flashType}`} style={{ marginBottom: '1rem' }}>
              {flash}
            </div>
          )}

          {error && (
            <div className="alert alert-error" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span>{error}</span>
              <button onClick={loadUsers} className="btn btn-sm btn-secondary">Retry</button>
            </div>
          )}

          {/* ── Toolbar ── */}
          <div className="toolbar" style={{ marginBottom: '1rem' }}>
            <input
              className="search-input"
              type="text"
              placeholder="Search by name or email…"
              value={search}
              onChange={e => setSearch(e.target.value)}
              aria-label="Search users"
            />
            <div className="filter-tabs">
              {['ALL', 'USER', 'STAFF', 'ADMIN'].map(r => (
                <button
                  key={r}
                  className={'filter-tab' + (roleFilter === r ? ' filter-tab-active' : '')}
                  onClick={() => setRoleFilter(r)}
                >
                  {r === 'ALL' ? 'All Roles' : r}
                </button>
              ))}
            </div>
            <div className="filter-tabs">
              {['ALL', 'ACTIVE', 'INACTIVE'].map(s => (
                <button
                  key={s}
                  className={'filter-tab' + (statusFilter === s ? ' filter-tab-active' : '')}
                  onClick={() => setStatusFilter(s)}
                >
                  {s === 'ALL' ? 'All Status' : s}
                </button>
              ))}
            </div>
          </div>

          {loading && (
            <div style={{ display: 'flex', justifyContent: 'center', padding: '4rem' }}>
              <Spinner size={32} />
            </div>
          )}

          {!loading && !error && filtered.length === 0 && (
            <div className="empty-state">
              <p>No users match your filters.</p>
            </div>
          )}

          {!loading && !error && filtered.length > 0 && (
            <div className="table-container">
              <table className="complaint-table">
                <thead>
                  <tr>
                    <th>#</th>
                    <th>Name</th>
                    <th>Email</th>
                    <th>Role</th>
                    <th>Status</th>
                    <th style={{ textAlign: 'center' }}>Total</th>
                    <th style={{ textAlign: 'center' }}>Open</th>
                    <th style={{ textAlign: 'center' }}>Resolved</th>
                    <th>Joined</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map(u => {
                    const busy = !!actionLoading[u.id]
                    const self_ = isSelf(u)
                    return (
                      <tr key={u.id} style={{ opacity: u.active ? 1 : 0.55 }}>
                        <td className="td-id">{u.id}</td>
                        <td style={{ fontWeight: 500 }}>
                          {u.firstName} {u.lastName}
                          {self_ && (
                            <span style={{ marginLeft: '0.5rem', fontSize: '0.7rem', color: 'var(--slate-5)', fontWeight: 400 }}>
                              (you)
                            </span>
                          )}
                        </td>
                        <td className="td-email">{u.email}</td>
                        <td><RoleBadge roles={u.roles} /></td>
                        <td><StatusDot active={u.active} /></td>
                        <td style={{ textAlign: 'center' }}>{u.totalComplaints}</td>
                        <td style={{ textAlign: 'center' }}>{u.openComplaints}</td>
                        <td style={{ textAlign: 'center' }}>{u.resolvedComplaints}</td>
                        <td className="td-date">
                          {u.createdAt
                            ? new Date(u.createdAt).toLocaleDateString('en-IN', {
                                day: 'numeric', month: 'short', year: 'numeric'
                              })
                            : '—'}
                        </td>
                        <td>
                          <div className="action-row">
                            <select
                              className="role-select"
                              value={currentRole(u)}
                              disabled={busy || self_}
                              onChange={(e) => handleChangeRole(u, e.target.value)}
                              aria-label={`Change role for ${u.firstName} ${u.lastName}`}
                              title={self_ ? 'Cannot change your own role' : 'Change role'}
                            >
                              <option value="USER">User</option>
                              <option value="STAFF">Staff</option>
                              <option value="ADMIN">Admin</option>
                            </select>

                            <button
                              className={`btn-action ${u.active ? 'btn-action-danger' : ''}`}
                              disabled={busy || self_}
                              onClick={() => handleToggleStatus(u)}
                              title={self_ ? 'Cannot deactivate yourself' : 'Toggle status'}
                            >
                              {busy ? '…' : u.active ? 'Deactivate' : 'Activate'}
                            </button>
                          </div>
                        </td>
                      </tr>
                    )
                  })}
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
