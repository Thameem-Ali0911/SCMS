import React, { useEffect, useState, useCallback } from 'react'
import {
    AreaChart, Area, BarChart, Bar, PieChart, Pie, Cell,
    XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer
} from 'recharts'
import { adminApi } from '../api/admin'
import Sidebar from '../components/Sidebar'
import { Navbar, PageHeader, Footer } from '../components/Layout'
import { Spinner } from '../components/UI'

/*
  AdminReports — analytics and reporting dashboard for admins.

  Sections:
  1. Summary KPI cards (total, open, resolved, avg resolution time, MoM change)
  2. Status breakdown — donut/pie chart
  3. Category breakdown — horizontal bar chart
  4. 30-day activity timeline — area chart
  5. Top complainants table

  MENTOR NOTE — recharts library:
  recharts is a React-native charting library built on D3. It uses
  composable React components (AreaChart, BarChart, PieChart) so you
  build charts declaratively the same way you build UI. No imperative
  canvas manipulation needed. We already have it in package.json.

  MENTOR NOTE — ResponsiveContainer:
  Wrapping every chart in <ResponsiveContainer width="100%" height={300}>
  makes them auto-resize with the viewport. Never hardcode pixel widths
  for charts — they'll overflow on mobile or look wrong in split-screen.
*/

// Colour palette consistent with global.css CSS variables
const CHART_COLORS = {
    SUBMITTED: '#6366f1',   // indigo
    IN_REVIEW: '#8b5cf6',   // violet
    IN_PROGRESS: '#f59e0b',   // amber
    RESOLVED: '#22c55e',   // green
    CLOSED: '#94a3b8',   // slate
    REJECTED: '#ef4444',   // red
    DRAFT: '#cbd5e1',   // light slate
}
const BAR_COLOR = '#6366f1'
const AREA_COLOR = '#6366f1'
const PIE_COLORS = Object.values(CHART_COLORS)

function KpiCard({ label, value, sub, accent }) {
    const accentMap = {
        blue: 'stat-card-blue',
        green: 'stat-card-green',
        amber: 'stat-card-amber',
        indigo: 'stat-card-indigo',
    }
    return (
        <div className={`stat-card ${accentMap[accent] || ''}`}>
            <span className="stat-card-value">{value}</span>
            <span className="stat-card-label">{label}</span>
            {sub && <span className="stat-card-sub">{sub}</span>}
        </div>
    )
}

function SectionCard({ title, children }) {
    return (
        <div className="section-card" style={{ marginBottom: '1.5rem' }}>
            <div className="section-card-header">
                <h3 className="section-card-title">{title}</h3>
            </div>
            <div style={{ padding: '1rem 1.25rem 1.25rem' }}>
                {children}
            </div>
        </div>
    )
}

// Custom tooltip for the area chart
function TimelineTooltip({ active, payload, label }) {
    if (!active || !payload?.length) return null
    return (
        <div style={{
            background: 'var(--surface-2)', border: '1px solid var(--border)',
            borderRadius: 8, padding: '0.5rem 0.75rem', fontSize: '0.8rem',
        }}>
            <div style={{ fontWeight: 600, marginBottom: 2 }}>{label}</div>
            <div style={{ color: AREA_COLOR }}>{payload[0].value} complaints</div>
        </div>
    )
}

function MoMBadge({ value }) {
    const positive = value >= 0
    return (
        <span style={{
            display: 'inline-flex', alignItems: 'center', gap: 3,
            color: positive ? 'var(--green-9)' : 'var(--red-9)',
            fontSize: '0.75rem', fontWeight: 600,
        }}>
            {positive ? '▲' : '▼'} {Math.abs(value)}% vs last month
        </span>
    )
}

export default function AdminReports() {
    const [summary, setSummary] = useState(null)
    const [byStatus, setByStatus] = useState([])
    const [byCategory, setByCategory] = useState([])
    const [byUser, setByUser] = useState([])
    const [timeline, setTimeline] = useState([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)

    const loadAll = useCallback(() => {
        setLoading(true)
        setError(null)
        Promise.all([
            adminApi.reportSummary(),
            adminApi.reportByStatus(),
            adminApi.reportByCategory(),
            adminApi.reportByUser(),
            adminApi.reportTimeline(),
        ])
            .then(([sum, status, cat, user, time]) => {
                setSummary(sum)
                setByStatus(status)
                setByCategory(cat)
                setByUser(user)
                // Format date for chart x-axis labels
                setTimeline(time.map(d => ({
                    ...d,
                    label: new Date(d.date).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })
                })))
            })
            .catch(() => setError('Failed to load report data.'))
            .finally(() => setLoading(false))
    }, [])

    useEffect(() => { loadAll() }, [loadAll])

    const formatHours = (h) => {
        if (h < 1) return '<1 hr'
        if (h < 24) return `${h} hrs`
        const days = (h / 24).toFixed(1)
        return `${days} days`
    }

    return (
        <div className="shell">
            <Sidebar />
            <div className="shell-main">
                <Navbar title="Reports & Analytics" />

                <div className="page-content">
                    <PageHeader
                        title="Reports"
                        subtitle="System-wide analytics — complaint volumes, resolution times, and trends."
                    />

                    {error && (
                        <div className="alert alert-error" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                            <span>{error}</span>
                            <button onClick={loadAll} className="btn btn-sm btn-secondary">Retry</button>
                        </div>
                    )}

                    {loading && (
                        <div style={{ display: 'flex', justifyContent: 'center', padding: '4rem' }}>
                            <Spinner size={32} />
                        </div>
                    )}

                    {!loading && !error && summary && (
                        <>
                            {/* ── Section 1: KPI summary cards ── */}
                            <div className="stat-grid" style={{ marginBottom: '1.5rem' }}>
                                <KpiCard
                                    label="Total Complaints"
                                    value={summary.totalComplaints}
                                />
                                <KpiCard
                                    label="Open"
                                    value={summary.openComplaints}
                                    accent="blue"
                                />
                                <KpiCard
                                    label="Resolved / Closed"
                                    value={summary.resolvedComplaints}
                                    accent="green"
                                />
                                <KpiCard
                                    label="Avg Resolution"
                                    value={formatHours(summary.avgResolutionHours)}
                                    accent="amber"
                                />
                                <KpiCard
                                    label="Total Users"
                                    value={summary.totalUsers}
                                    sub={`${summary.activeUsers} active`}
                                    accent="indigo"
                                />
                            </div>

                            {/* Month-over-month banner */}
                            <div className="section-card" style={{ marginBottom: '1.5rem', padding: '0.9rem 1.25rem' }}>
                                <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', flexWrap: 'wrap' }}>
                                    <span style={{ fontWeight: 600, color: 'var(--text-1)' }}>
                                        This month: {summary.complaintsThisMonth} complaints
                                    </span>
                                    <span style={{ color: 'var(--text-2)', fontSize: '0.85rem' }}>
                                        Last month: {summary.complaintsLastMonth}
                                    </span>
                                    <MoMBadge value={summary.monthOverMonthChange} />
                                </div>
                            </div>

                            {/* ── Section 2: Charts row ── */}
                            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem', marginBottom: '1.5rem' }}>

                                {/* Status Pie Chart */}
                                <SectionCard title="Complaints by Status">
                                    <ResponsiveContainer width="100%" height={260}>
                                        <PieChart>
                                            <Pie
                                                data={byStatus}
                                                dataKey="count"
                                                nameKey="status"
                                                cx="50%"
                                                cy="50%"
                                                outerRadius={90}
                                                innerRadius={50}
                                                paddingAngle={3}
                                                label={({ status, percentage }) => `${status} ${percentage}%`}
                                                labelLine={false}
                                            >
                                                {byStatus.map((entry, i) => (
                                                    <Cell
                                                        key={entry.status}
                                                        fill={CHART_COLORS[entry.status] || PIE_COLORS[i % PIE_COLORS.length]}
                                                    />
                                                ))}
                                            </Pie>
                                            <Tooltip
                                                formatter={(val, name) => [val, name]}
                                                contentStyle={{
                                                    background: 'var(--surface-2)',
                                                    border: '1px solid var(--border)',
                                                    borderRadius: 8,
                                                    fontSize: '0.8rem',
                                                }}
                                            />
                                            <Legend
                                                iconType="circle"
                                                iconSize={8}
                                                wrapperStyle={{ fontSize: '0.75rem' }}
                                            />
                                        </PieChart>
                                    </ResponsiveContainer>
                                </SectionCard>

                                {/* Category Bar Chart */}
                                <SectionCard title="Complaints by Category">
                                    {byCategory.length === 0 ? (
                                        <div className="empty-inline">No category data yet.</div>
                                    ) : (
                                        <ResponsiveContainer width="100%" height={260}>
                                            <BarChart
                                                data={byCategory}
                                                layout="vertical"
                                                margin={{ top: 0, right: 20, left: 10, bottom: 0 }}
                                            >
                                                <CartesianGrid strokeDasharray="3 3" horizontal={false} stroke="var(--border)" />
                                                <XAxis
                                                    type="number"
                                                    tick={{ fontSize: 11, fill: 'var(--text-3)' }}
                                                    axisLine={false}
                                                    tickLine={false}
                                                />
                                                <YAxis
                                                    dataKey="category"
                                                    type="category"
                                                    width={90}
                                                    tick={{ fontSize: 11, fill: 'var(--text-2)' }}
                                                    axisLine={false}
                                                    tickLine={false}
                                                />
                                                <Tooltip
                                                    contentStyle={{
                                                        background: 'var(--surface-2)',
                                                        border: '1px solid var(--border)',
                                                        borderRadius: 8,
                                                        fontSize: '0.8rem',
                                                    }}
                                                />
                                                <Bar dataKey="count" fill={BAR_COLOR} radius={[0, 4, 4, 0]} />
                                            </BarChart>
                                        </ResponsiveContainer>
                                    )}
                                </SectionCard>
                            </div>

                            {/* ── Section 3: 30-day timeline ── */}
                            <SectionCard title="Daily Activity — Last 30 Days">
                                <ResponsiveContainer width="100%" height={220}>
                                    <AreaChart data={timeline} margin={{ top: 5, right: 20, left: 0, bottom: 0 }}>
                                        <defs>
                                            <linearGradient id="areaGradient" x1="0" y1="0" x2="0" y2="1">
                                                <stop offset="5%" stopColor={AREA_COLOR} stopOpacity={0.25} />
                                                <stop offset="95%" stopColor={AREA_COLOR} stopOpacity={0.02} />
                                            </linearGradient>
                                        </defs>
                                        <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" vertical={false} />
                                        <XAxis
                                            dataKey="label"
                                            tick={{ fontSize: 10, fill: 'var(--text-3)' }}
                                            axisLine={false}
                                            tickLine={false}
                                            interval={4}
                                        />
                                        <YAxis
                                            allowDecimals={false}
                                            tick={{ fontSize: 10, fill: 'var(--text-3)' }}
                                            axisLine={false}
                                            tickLine={false}
                                            width={28}
                                        />
                                        <Tooltip content={<TimelineTooltip />} />
                                        <Area
                                            type="monotone"
                                            dataKey="count"
                                            stroke={AREA_COLOR}
                                            strokeWidth={2}
                                            fill="url(#areaGradient)"
                                            dot={false}
                                            activeDot={{ r: 4, strokeWidth: 2 }}
                                        />
                                    </AreaChart>
                                </ResponsiveContainer>
                            </SectionCard>

                            {/* ── Section 4: Top complainants table ── */}
                            <SectionCard title="Top Complainants">
                                {byUser.length === 0 ? (
                                    <div className="empty-inline">No complaint data yet.</div>
                                ) : (
                                    <div className="table-container" style={{ border: 'none', boxShadow: 'none', padding: 0 }}>
                                        <table className="complaint-table">
                                            <thead>
                                                <tr>
                                                    <th>Rank</th>
                                                    <th>Name</th>
                                                    <th>Email</th>
                                                    <th style={{ textAlign: 'center' }}>Total</th>
                                                    <th style={{ textAlign: 'center' }}>Open</th>
                                                    <th style={{ textAlign: 'center' }}>Resolved</th>
                                                    <th>Activity</th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                {byUser.map((u, i) => {
                                                    const pct = byUser[0]?.total > 0
                                                        ? Math.round((u.total / byUser[0].total) * 100)
                                                        : 0
                                                    return (
                                                        <tr key={u.userId}>
                                                            <td style={{ fontWeight: 600, color: 'var(--text-3)', width: 40 }}>
                                                                #{i + 1}
                                                            </td>
                                                            <td style={{ fontWeight: 500 }}>{u.userName}</td>
                                                            <td className="td-email">{u.email}</td>
                                                            <td style={{ textAlign: 'center', fontWeight: 600 }}>{u.total}</td>
                                                            <td style={{ textAlign: 'center' }}>{u.open}</td>
                                                            <td style={{ textAlign: 'center' }}>{u.resolved}</td>
                                                            <td style={{ width: 120 }}>
                                                                <div style={{
                                                                    height: 6, borderRadius: 3,
                                                                    background: 'var(--surface-3)',
                                                                    overflow: 'hidden',
                                                                }}>
                                                                    <div style={{
                                                                        height: '100%',
                                                                        width: `${pct}%`,
                                                                        background: 'var(--accent)',
                                                                        borderRadius: 3,
                                                                        transition: 'width 0.4s ease',
                                                                    }} />
                                                                </div>
                                                            </td>
                                                        </tr>
                                                    )
                                                })}
                                            </tbody>
                                        </table>
                                    </div>
                                )}
                            </SectionCard>
                        </>
                    )}
                </div>

                <Footer />
            </div>
        </div>
    )
}
