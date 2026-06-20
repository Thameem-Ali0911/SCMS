import React from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

/*
  Sidebar — persistent left navigation.

  Role-aware: ADMIN sees All Complaints + Users + Reports.
  USER (student) sees only My Complaints + New Complaint + Track.

  NavLink automatically applies the "active" class when the current
  URL matches its `to` prop — no manual comparison needed.
*/

const NAV_ICON = {
  dashboard:  'M3 9l9-7 9 7v11a2 2 0 01-2 2H5a2 2 0 01-2-2z M9 22V12h6v10',
  complaints: 'M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2',
  new:        'M12 4v16m8-8H4',
  users:      'M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2 M23 21v-2a4 4 0 00-3-3.87 M16 3.13a4 4 0 010 7.75',
  reports:    'M9 17v-2m3 2v-4m3 4v-6m2 10H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z',
  logout:     'M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1',
}

function NavIcon({ d }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"
         strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"
         style={{ width: 18, height: 18, flexShrink: 0 }}>
      {d.split(' M').map((segment, i) => (
        <path key={i} d={i === 0 ? segment : 'M' + segment} />
      ))}
    </svg>
  )
}

export default function Sidebar() {
  const { user, isAdmin, logout } = useAuth()
  const navigate = useNavigate()

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  const adminLinks = [
    { to: '/dashboard',   label: 'Dashboard',       icon: 'dashboard'  },
    { to: '/complaints',  label: 'All Complaints',  icon: 'complaints' },
    { to: '/users',       label: 'Users',           icon: 'users'      },
    { to: '/reports',     label: 'Reports',         icon: 'reports'    },
  ]

  const userLinks = [
    { to: '/dashboard',            label: 'Dashboard',      icon: 'dashboard'  },
    { to: '/complaints',           label: 'My Complaints',  icon: 'complaints' },
    { to: '/complaints/new',       label: 'New Complaint',  icon: 'new'        },
  ]

  const links = isAdmin() ? adminLinks : userLinks

  return (
    <aside className="sidebar">
      {/* Brand */}
      <div className="sidebar-brand">
        <div className="sidebar-brand-mark">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"
               strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M9 12l2 2 4-4"/>
            <path d="M21 12c0 4.97-4.03 9-9 9S3 16.97 3 12 7.03 3 12 3s9 4.03 9 9z"/>
          </svg>
        </div>
        <span className="sidebar-brand-name">SCMS</span>
      </div>

      {/* Role badge */}
      <div className="sidebar-role-badge">
        {isAdmin() ? 'Administrator' : 'Student'}
      </div>

      {/* Navigation */}
      <nav className="sidebar-nav">
        <p className="sidebar-section-label">Menu</p>
        {links.map(({ to, label, icon }) => (
          <NavLink
            key={to}
            to={to}
            end={to === '/dashboard'}
            className={({ isActive }) =>
              'sidebar-link' + (isActive ? ' sidebar-link-active' : '')
            }
          >
            <NavIcon d={NAV_ICON[icon]} />
            <span>{label}</span>
          </NavLink>
        ))}
      </nav>

      {/* User profile + logout at bottom */}
      <div className="sidebar-footer">
        <div className="sidebar-user">
          <div className="sidebar-avatar">
            {user?.firstName?.[0]}{user?.lastName?.[0]}
          </div>
          <div className="sidebar-user-info">
            <span className="sidebar-user-name">
              {user?.firstName} {user?.lastName}
            </span>
            <span className="sidebar-user-email">{user?.email}</span>
          </div>
        </div>
        <button className="sidebar-logout" onClick={handleLogout}>
          <NavIcon d={NAV_ICON.logout} />
          Sign out
        </button>
      </div>
    </aside>
  )
}
