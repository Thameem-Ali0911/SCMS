import React from 'react'

/*
  StatusBadge — coloured pill for complaint status.

  Maps each status string to a CSS modifier class. Adding a new status
  only requires:
    1. Adding an entry to STATUS_META
    2. Adding the corresponding CSS class in global.css
  No logic changes needed anywhere else.
*/
const STATUS_META = {
  DRAFT:       { label: 'Draft',       cls: 'badge-slate'  },
  SUBMITTED:   { label: 'Submitted',   cls: 'badge-blue'   },
  IN_REVIEW:   { label: 'In Review',   cls: 'badge-indigo' },
  IN_PROGRESS: { label: 'In Progress', cls: 'badge-amber'  },
  RESOLVED:    { label: 'Resolved',    cls: 'badge-green'  },
  CLOSED:      { label: 'Closed',      cls: 'badge-slate'  },
  REJECTED:    { label: 'Rejected',    cls: 'badge-red'    },
}

const PRIORITY_META = {
  LOW:      { label: 'Low',      cls: 'badge-slate'  },
  MEDIUM:   { label: 'Medium',   cls: 'badge-blue'   },
  HIGH:     { label: 'High',     cls: 'badge-amber'  },
  CRITICAL: { label: 'Critical', cls: 'badge-red'    },
}

export function StatusBadge({ status }) {
  const meta = STATUS_META[status] ?? { label: status, cls: 'badge-slate' }
  return (
    <span className={`badge ${meta.cls}`}>{meta.label}</span>
  )
}

export function PriorityBadge({ priority }) {
  const meta = PRIORITY_META[priority] ?? { label: priority, cls: 'badge-slate' }
  return (
    <span className={`badge ${meta.cls}`}>{meta.label}</span>
  )
}

/*
  StatCard — a single KPI tile on the dashboard.
  Props: label, value, sub (optional), colorClass (optional CSS mod)
*/
export function StatCard({ label, value, sub, colorClass = '' }) {
  return (
    <div className={`stat-card ${colorClass}`}>
      <span className="stat-card-value">{value}</span>
      <span className="stat-card-label">{label}</span>
      {sub && <span className="stat-card-sub">{sub}</span>}
    </div>
  )
}

/*
  EmptyState — shown when a list has no items.
*/
export function EmptyState({ message = 'Nothing here yet.', action }) {
  return (
    <div className="empty-state">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"
           strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"
           style={{ width: 40, height: 40, color: 'var(--slate-5)' }}>
        <path d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2
                 M9 5a2 2 0 002 2h2a2 2 0 002-2
                 M9 5a2 2 0 012-2h2a2 2 0 012 2" />
      </svg>
      <p>{message}</p>
      {action}
    </div>
  )
}

/*
  Spinner — inline loading indicator.
*/
export function Spinner({ size = 20 }) {
  return (
    <span className="spinner" style={{ width: size, height: size }} aria-label="Loading" />
  )
}
