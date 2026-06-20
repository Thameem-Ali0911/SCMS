import React, { useEffect, useState, useCallback } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { complaintsApi } from '../api/complaints'
import { useToast } from '../components/Toast'
import Sidebar from '../components/Sidebar'
import { Navbar, PageHeader, Footer } from '../components/Layout'
import { StatusBadge, PriorityBadge, Spinner, EmptyState } from '../components/UI'

/*
  StaffQueue — a staff member's personal complaint workload view.

  Available to: STAFF and ADMIN (StaffRoute in App.jsx)

  Two tabs:
    "My Queue"      — complaints currently assigned to this staff member
    "Unassigned"    — complaints with no assignee (pick-up queue)

  Features:
    • Self-assign from the Unassigned tab (one click → complaint moves to My Queue)
    • Quick status update without opening the detail page
    • Priority sort (CRITICAL → HIGH → MEDIUM → LOW)
    • Filter by status within each tab

  MENTOR NOTE — why a separate page from ComplaintList?
  ComplaintList shows ALL complaints — it's a management view for admin.
  StaffQueue is a work-queue view — it shows what THIS staff member needs to
  act on. Mixing them would require complex filters. Two focused views > one
  Swiss-army-knife view.
*/

const PRIORITY_ORDER = { CRITICAL: 0, HIGH: 1, MEDIUM: 2, LOW: 3 }

export default function StaffQueue() {
  const { user } = useAuth()
  const toast     = useToast()

  const [tab,        setTab]        = useState('mine')    // 'mine' | 'unassigned'
  const [all,        setAll]        = useState([])
  const [loading,    setLoading]    = useState(true)
  const [assigning,  setAssigning]  = useState({})        // { [id]: true }

  const mine       = all.filter(c => c.assignedToId !== null)
  const unassigned = all.filter(c => c.assignedToId === null
    && !['RESOLVED', 'CLOSED', 'REJECTED'].includes(c.status))

  const displayed  = tab === 'mine' ? mine : unassigned

  const load = useCallback(() => {
    setLoading(true)
    complaintsApi.list()
      .then(data => {
        // Sort by priority then by submitted date (oldest first for FIFO)
        const sorted = [...data].sort((a, b) => {
          const pDiff = (PRIORITY_ORDER[a.priority] ?? 3) - (PRIORITY_ORDER[b.priority] ?? 3)
          if (pDiff !== 0) return pDiff
          return new Date(a.submittedAt) - new Date(b.submittedAt)
        })
        setAll(sorted)
      })
      .catch(() => toast.error('Failed to load complaints.'))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => { load() }, [load])

  const handleSelfAssign = async (c) => {
    setAssigning(prev => ({ ...prev, [c.id]: true }))
    try {
      const updated = await complaintsApi.selfAssign(c.id)
      // Replace in local state — no re-fetch needed
      setAll(prev => prev.map(x => x.id === updated.id ? updated : x))
      toast.success(`Complaint #${c.id} assigned to you. Status → In Review.`)
      // Switch to My Queue tab so user sees it there
      setTab('mine')
    } catch (err) {
      toast.error(err.response?.data?.message ?? 'Failed to assign complaint.')
    } finally {
      setAssigning(prev => ({ ...prev, [c.id]: false }))
    }
  }

  return (
    <div className="shell">
      <Sidebar />
      <div className="shell-main">
        <Navbar title="My Queue" />

        <div className="page-content">
          <PageHeader
            title="My Queue"
            subtitle="Complaints assigned to you and unassigned complaints available to pick up."
          />

          {/* ── Tab switcher ── */}
          <div style={{ display: 'flex', gap: 4, marginBottom: '1.25rem' }}>
            {[
              { key: 'mine',       label: `My Queue (${mine.length})` },
              { key: 'unassigned', label: `Unassigned (${unassigned.length})` },
            ].map(t => (
              <button
                key={t.key}
                className={'filter-tab' + (tab === t.key ? ' filter-tab-active' : '')}
                onClick={() => setTab(t.key)}
              >
                {t.label}
              </button>
            ))}
          </div>

          {loading && (
            <div style={{ display: 'flex', justifyContent: 'center', padding: '4rem' }}>
              <Spinner size={32} />
            </div>
          )}

          {!loading && displayed.length === 0 && (
            <EmptyState
              message={
                tab === 'mine'
                  ? 'No complaints assigned to you. Pick some up from the Unassigned tab.'
                  : 'No unassigned complaints right now. Check back later.'
              }
            />
          )}

          {!loading && displayed.length > 0 && (
            <div className="table-container">
              <table className="complaint-table">
                <thead>
                  <tr>
                    <th>#</th>
                    <th>Subject</th>
                    <th>Category</th>
                    <th>Priority</th>
                    <th>Status</th>
                    <th>Filed by</th>
                    <th>Submitted</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {displayed.map(c => (
                    <tr key={c.id} style={{
                      // Highlight CRITICAL complaints
                      background: c.priority === 'CRITICAL'
                        ? 'rgba(239,68,68,0.04)' : undefined,
                    }}>
                      <td className="td-id">#{c.id}</td>
                      <td className="td-subject">
                        <Link to={`/complaints/${c.id}`} className="link-subject">
                          {c.subject}
                        </Link>
                      </td>
                      <td style={{ fontSize: '0.82rem' }}>{c.category}</td>
                      <td><PriorityBadge priority={c.priority} /></td>
                      <td><StatusBadge   status={c.status}   /></td>
                      <td className="td-email">{c.submittedByName}</td>
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
                          {tab === 'unassigned' && (
                            <button
                              className="btn-action"
                              style={{ color: 'var(--indigo)', borderColor: 'var(--indigo)' }}
                              onClick={() => handleSelfAssign(c)}
                              disabled={!!assigning[c.id]}
                            >
                              {assigning[c.id] ? '…' : 'Assign to Me'}
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
