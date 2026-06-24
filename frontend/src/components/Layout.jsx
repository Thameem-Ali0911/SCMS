import React from 'react'
import { useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useSidebar } from '../context/SidebarContext'

/*
  Navbar — top bar shown inside the authenticated shell.
  Shows the current page title (derived from the URL) and a global
  search box (wired in the complaint list page).

  CHANGE in v2.0: added the mobile hamburger menu button — see
  Sidebar.jsx / SidebarContext.jsx for the off-canvas drawer this opens.
  Hidden on desktop widths via .navbar-menu-btn's CSS media query.
*/
export function Navbar({ title, actions }) {
  const { toggle } = useSidebar()

  return (
    <header className="navbar">
      <div className="navbar-left">
        <button
          className="navbar-menu-btn"
          onClick={toggle}
          aria-label="Open navigation menu"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
               strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M3 12h18M3 6h18M3 18h18"/>
          </svg>
        </button>
        <h1 className="navbar-title">{title}</h1>
      </div>
      <div className="navbar-right">
        {actions}
      </div>
    </header>
  )
}

/*
  PageHeader — the section just below the Navbar inside a page's content area.
  Used as a consistent "breadcrumb + title + subtitle + CTA" row.

  Props:
    title       — main heading (e.g. "All Complaints")
    subtitle    — optional helper text
    action      — optional React node (a button, link, etc.)
    badge       — optional pill (e.g. complaint count)
*/
export function PageHeader({ title, subtitle, action, badge }) {
  return (
    <div className="page-header">
      <div className="page-header-left">
        <div className="page-header-title-row">
          <h2 className="page-header-title">{title}</h2>
          {badge !== undefined && (
            <span className="page-header-badge">{badge}</span>
          )}
        </div>
        {subtitle && (
          <p className="page-header-subtitle">{subtitle}</p>
        )}
      </div>
      {action && (
        <div className="page-header-action">{action}</div>
      )}
    </div>
  )
}

/*
  Footer — minimal footer inside the authenticated shell.
  Keep it light — this isn't a marketing site.
*/
export function Footer() {
  return (
    <footer className="shell-footer">
      <span>SCMS &copy; {new Date().getFullYear()}</span>
      <span>GEC Wayanad</span>
    </footer>
  )
}
