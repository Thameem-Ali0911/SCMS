import React, { useEffect, useState, useCallback } from 'react'
import { Link } from 'react-router-dom'
import { complaintsApi } from '../api/complaints'
import { useToast } from '../components/Toast'
import Sidebar from '../components/Sidebar'
import { Navbar, PageHeader, Footer } from '../components/Layout'
import { StatusBadge, PriorityBadge, Spinner, EmptyState, Pagination } from '../components/UI'

/*
  StaffQueue — a staff member's personal complaint workload view.

  Available to: STAFF and ADMIN (StaffRoute in App.jsx)

  Two tabs:
    "My Queue"    — complaints currently assigned to this staff member
    "Unassigned"  — complaints with no assignee yet (priority-ordered pick-up queue)

  CHANGE in v2.0 (production hardening):
    v1.3 called complaintsApi.list() once, loaded EVERY complaint in the
    system, and split it into "mine"/"unassigned" with a client-side
    .filter() + .sort() — exactly the kind of unbounded fetch the v1.3
    report flagged elsewhere ("the browser will freeze"). It also called
    complaintsApi.selfAssign(), which didn't exist anywhere in api/complaints.js
    — a guaranteed runtime error the moment "Assign to Me" was clicked.

    v2.0 uses two dedicated, paginated, server-sorted endpoints instead:
    GET /complaints/queue/mine and GET /complaints/queue/unassigned (the
    unassigned one is already priority-then-age ordered by the database —
    see ComplaintRepository.findUnassignedQueue()). selfAssign() now exists
    and calls the real backend endpoint.
*/

const PAGE_SIZE = 20

export default function StaffQueue() {
  const toast = useToast()

  const [tab, setTab] = useState('mine') // 'mine' | 'unassigned'
  const [minePage,       setMinePage]       = useState({ content: [], page: 0, totalPages: 0, totalElements: 0 })
  const [unassignedPage, setUnassignedPage] = useState({ content: [], page: 0, totalPages: 0, totalElements: 0 })
  const [mineNumber,       setMineNumber]       = useState(0)
  const [unassignedNumber, setUnassignedNumber] = useState(0)
  const [loading,   setLoading]   = useState(true)
  const [assigning, setAssigning] = useState({}) // { [id]: true }

  const loadMine = useCallback(() => {
    return complaintsApi.queueMine({ page: mineNumber, size: PAGE_SIZE }).then(setMinePage)
  }, [mineNumber])

  const loadUnassigned = useCallback(() => {
    return complaintsApi.queueUnassigned({ page: unassignedNumber, size: PAGE_SIZE }).then(setUnassignedPage)
  }, [unassignedNumber])

  const loadAll = useCallback(() => {
    setLoading(true)
    Promise.all([loadMine(), loadUnassigned()])
      .catch(() => toast.error('Failed to load your queue.'))
      .finally(() => setLoading(false))
  }, [loadMine, loadUnassigned])

  useEffect(() => { loadAll() }, [loadAll])

  const handleSelfAssign = async (c) => {
    setAssigning(prev => ({ ...prev, [c.id]: true }))
    try {
      await complaintsApi.selfAssign(c.id)
      toast.success(`Complaint #${c.id} assigned to you. Status → In Review.`)
      setTab('mine')
      await loadAll()
    } catch (err) {
      toast.error(err.response?.data?.message ?? 'Failed to assign complaint.')
    } finally {
      setAssigning(prev => ({ ...prev, [c.id]: false }))
    }
  }

  const displayedPage   = tab === 'mine' ? minePage : unassignedPage
  const displayedNumber = tab === 'mine' ? mineNumber : unassignedNumber
  const setDisplayedNumber = tab === 'mine' ? setMineNumber : setUnassignedNumber
  const rows = displayedPage.content

  return (
    <div className="shell">
      <Sidebar />
      <div className="shell-main">
        <Navbar title="My Queue" />

        <div className="page-content">
          <PageHeader
            title="My Queue"
            subtitle="Complaints assigned to you, and unassigned complaints available to pick up."
          />

          {/* ── Tab switcher ── */}
          <div style={{ display: 'flex', gap: 4, marginBottom: '1.25rem' }} role="tablist">
            {[
              { key: 'mine',       label: `My Queue (${minePage.totalElements})` },
              { key: 'unassigned', label: `Unassigned (${unassignedPage.totalElements})` },
            ].map(t => (
              <button
                key={t.key}
                role="tab"
                aria-selected={tab === t.key}
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

          {!loading && rows.length === 0 && (
            <EmptyState
              message={
                tab === 'mine'
                  ? 'No complaints assigned to you. Pick some up from the Unassigned tab.'
                  : 'No unassigned complaints right now. Check back later.'
              }
            />
          )}

          {!loading && rows.length > 0 && (
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
                  {rows.map(c => (
                    <tr key={c.id} style={{
                      background: c.priority === 'CRITICAL' ? 'rgba(239,68,68,0.04)' : undefined,
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
              <Pagination
                page={displayedPage.page}
                totalPages={displayedPage.totalPages}
                totalElements={displayedPage.totalElements}
                onPageChange={setDisplayedNumber}
              />
            </div>
          )}
        </div>

        <Footer />
      </div>
    </div>
  )
}
