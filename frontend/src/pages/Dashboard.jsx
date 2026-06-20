import React, { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { complaintsApi } from '../api/complaints'
import Sidebar from '../components/Sidebar'
import { Navbar, PageHeader, Footer } from '../components/Layout'
import { StatCard, StatusBadge, Spinner } from '../components/UI'

/*
  Dashboard — landing page after login.

  Admin sees:  Total / Open / In-Progress / Resolved / Total Users
  Student sees: My Total / My Open / My In-Progress / My Resolved

  Below the stats: the 5 most recent complaints for quick access.
*/
export default function Dashboard() {
  const { user, isAdmin } = useAuth()
  const admin = isAdmin()

  const [stats, setStats] = useState(null)
  const [recent, setRecent] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [retryTrigger, setRetryTrigger] = useState(0)

  const loadData = () => {
    setRetryTrigger(prev => prev + 1)
  }

  useEffect(() => {
    let isMounted = true
    const controller = new AbortController()

    const fetchData = async () => {
      try {
        setLoading(true)
        setError(null)
        const [s, list] = await Promise.all([
          complaintsApi.stats(),
          complaintsApi.list(),
        ])
        if (isMounted) {
          setStats(s)
          setRecent(list.slice(0, 5))
        }
      } catch (err) {
        if (isMounted) {
          setError('Failed to load dashboard data. Is the backend running?')
        }
      } finally {
        if (isMounted) {
          setLoading(false)
        }
      }
    }

    fetchData()

    return () => {
      isMounted = false
      controller.abort()
    }
  }, [admin, retryTrigger])

  return (
    <div className="shell">
      <Sidebar />
      <div className="shell-main">
        <Navbar
          title="Dashboard"
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
            title={`Welcome back, ${user?.firstName} 👋`}
            subtitle={admin
              ? "Here's what's happening across the system today."
              : "You haven't filed any complaints yet."}
          />

          {loading && (
            <div style={{ display: 'flex', justifyContent: 'center', padding: '3rem' }}>
              <Spinner size={32} />
            </div>
          )}

          {error && (
            <div className="alert alert-error" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span>{error}</span>
              <button onClick={loadData} className="btn btn-sm btn-secondary">
                Retry
              </button>
            </div>
          )}

          {!loading && stats && (
            <>
              {/* ── KPI stat cards ── */}
              <div className="stat-grid">
                <StatCard
                  label={admin ? 'Total Complaints' : 'My Complaints'}
                  value={stats.total}
                />
                <StatCard
                  label="Open / Submitted"
                  value={stats.submitted}
                  colorClass="stat-card-blue"
                />
                <StatCard
                  label="In Progress"
                  value={stats.inProgress}
                  colorClass="stat-card-amber"
                />
                <StatCard
                  label="Resolved"
                  value={stats.resolved}
                  colorClass="stat-card-green"
                />
                {admin && (
                  <StatCard
                    label="Total Users"
                    value={stats.totalUsers}
                    colorClass="stat-card-indigo"
                  />
                )}
              </div>

              {/* ── Recent complaints preview ── */}
              <div className="section-card">
                <div className="section-card-header">
                  <h3 className="section-card-title">
                    {admin ? 'Recent Complaints' : 'My Recent Complaints'}
                  </h3>
                  <Link to="/complaints" className="link-subtle">View all →</Link>
                </div>

                {recent.length === 0 ? (
                  <div className="empty-inline">
                    {admin
                      ? 'No complaints filed yet.'
                      : 'You haven\'t filed any complaints yet. '}
                    {!admin && (
                      <Link to="/complaints/new">File your first one →</Link>
                    )}
                  </div>
                ) : (
                  <div className="complaint-table-mini">
                    {recent.map(c => (
                      <Link
                        key={c.id}
                        to={`/complaints/${c.id}`}
                        className="complaint-row-mini"
                      >
                        <div className="complaint-row-mini-left">
                          <span className="complaint-row-mini-id">#{c.id}</span>
                          <span className="complaint-row-mini-subject">{c.subject}</span>
                        </div>
                        <div className="complaint-row-mini-right">
                          <StatusBadge status={c.status} />
                          <span className="complaint-row-mini-cat">{c.category}</span>
                        </div>
                      </Link>
                    ))}
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
