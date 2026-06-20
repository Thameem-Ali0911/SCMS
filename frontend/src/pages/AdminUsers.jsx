import React, { useEffect, useState, useCallback } from 'react'
import { useAuth } from '../context/AuthContext'
import { adminApi } from '../api/admin'
import Sidebar from '../components/Sidebar'
import { Navbar, PageHeader, Footer } from '../components/Layout'
import { Spinner } from '../components/UI'

/*
  AdminUsers — full user management page for admins.

  Features:
  - List all users with complaint counts
  - Search by name or email
  - Filter by role and status
  - Promote/demote role (USER ↔ ADMIN)
  - Activate / deactivate account
  - Visual distinction: active vs inactive users

  MENTOR NOTE — optimistic UI updates:
  After a successful API call (toggle status, change role) we update the
  local state immediately instead of re-fetching the full list. This makes
  the UI feel instant. The pattern:
    1. Call the API
    2. On success, replace the updated user in the local array
    3. On failure, show an error — do NOT update local state
  This is safer than re-fetching because a re-fetch could lose the user's
  scroll position and search filters.
*/

function RoleBadge({ roles }) {
    const isAdmin = roles?.includes('ADMIN')
    return (
        <span className={`badge ${isAdmin ? 'badge-indigo' : 'badge-slate'}`}>
            {isAdmin ? 'Admin' : 'User'}
        </span>
    )
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

export default function AdminUsers() {
    const { user: self } = useAuth()

    const [users, setUsers] = useState([])
    const [filtered, setFiltered] = useState([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)
    const [flash, setFlash] = useState(null)
    const [flashType, setFlashType] = useState('success')

    // Filters
    const [search, setSearch] = useState('')
    const [roleFilter, setRoleFilter] = useState('ALL')
    const [statusFilter, setStatusFilter] = useState('ALL')

    // Per-row action loading state (userId → true/false)
    const [actionLoading, setActionLoading] = useState({})

    const loadUsers = useCallback(() => {
        setLoading(true)
        adminApi.listUsers()
            .then(data => setUsers(data))
            .catch(() => setError('Failed to load users. Is the backend running?'))
            .finally(() => setLoading(false))
    }, [])

    useEffect(() => { loadUsers() }, [loadUsers])

    // Client-side filter whenever search/filter state changes
    useEffect(() => {
        let result = [...users]
        if (search.trim()) {
            const q = search.toLowerCase()
            result = result.filter(u =>
                u.firstName.toLowerCase().includes(q) ||
                u.lastName.toLowerCase().includes(q) ||
                u.email.toLowerCase().includes(q)
            )
        }
        if (roleFilter !== 'ALL') {
            result = result.filter(u =>
                roleFilter === 'ADMIN' ? u.roles?.includes('ADMIN') : !u.roles?.includes('ADMIN')
            )
        }
        if (statusFilter !== 'ALL') {
            result = result.filter(u =>
                statusFilter === 'ACTIVE' ? u.active : !u.active
            )
        }
        setFiltered(result)
    }, [users, search, roleFilter, statusFilter])

    const showFlash = (msg, type = 'success') => {
        setFlash(msg)
        setFlashType(type)
        setTimeout(() => setFlash(null), 4000)
    }

    const setRowLoading = (id, val) =>
        setActionLoading(prev => ({ ...prev, [id]: val }))

    const handleToggleStatus = async (u) => {
        if (u.id === self?.userId) return
        const newActive = !u.active
        const label = newActive ? 'activated' : 'deactivated'
        setRowLoading(u.id, true)
        try {
            const updated = await adminApi.toggleUserStatus(u.id, newActive)
            setUsers(prev => prev.map(x => x.id === u.id ? updated : x))
            showFlash(`${updated.firstName} ${updated.lastName} ${label}.`)
        } catch (err) {
            showFlash(err.response?.data?.message ?? `Failed to ${label} user.`, 'error')
        } finally {
            setRowLoading(u.id, false)
        }
    }

    const handleChangeRole = async (u) => {
        if (u.id === self?.userId) return
        const newRole = u.roles?.includes('ADMIN') ? 'USER' : 'ADMIN'
        const label = newRole === 'ADMIN' ? 'promoted to Admin' : 'demoted to User'
        if (!window.confirm(`Are you sure you want to ${label.replace(' to ', ' ')} ${u.firstName}?`)) return
        setRowLoading(u.id, true)
        try {
            const updated = await adminApi.changeUserRole(u.id, newRole)
            setUsers(prev => prev.map(x => x.id === u.id ? updated : x))
            showFlash(`${updated.firstName} ${updated.lastName} ${label}.`)
        } catch (err) {
            showFlash(err.response?.data?.message ?? 'Failed to change role.', 'error')
        } finally {
            setRowLoading(u.id, false)
        }
    }

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
                        badge={filtered.length}
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
                        />
                        <div className="filter-tabs">
                            {['ALL', 'USER', 'ADMIN'].map(r => (
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
                                                        <span style={{
                                                            marginLeft: '0.5rem',
                                                            fontSize: '0.7rem',
                                                            color: 'var(--slate-5)',
                                                            fontWeight: 400
                                                        }}>(you)</span>
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
                                                        {/* Role toggle */}
                                                        <button
                                                            className="btn-action"
                                                            disabled={busy || self_}
                                                            onClick={() => handleChangeRole(u)}
                                                            title={self_ ? 'Cannot change your own role' : 'Toggle role'}
                                                        >
                                                            {busy ? '…' : u.roles?.includes('ADMIN') ? 'Demote' : 'Promote'}
                                                        </button>

                                                        {/* Status toggle */}
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
                        </div>
                    )}
                </div>

                <Footer />
            </div>
        </div>
    )
}
