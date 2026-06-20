import React from 'react'

/*
  BrandPanel — the dark left side of the auth split layout.
  Shared between LoginPage and RegisterPage.

  Contains:
    - Logo mark + wordmark
    - Headline copy specific to complaint management
    - Three mock "ticket" cards that visualise the product
    - A footer tag line
    - The animated gradient mesh (via CSS ::before)
    - The grid overlay (via CSS ::after)
*/
export default function BrandPanel() {
  return (
    <div className="auth-brand">
      <div className="auth-brand-content">

        {/* Logo */}
        <div className="brand-logo">
          <div className="brand-logo-mark">
            <svg viewBox="0 0 24 24">
              <path d="M9 12l2 2 4-4"/>
              <path d="M21 12c0 4.97-4.03 9-9 9S3 16.97 3 12 7.03 3 12 3s9 4.03 9 9z"/>
            </svg>
          </div>
          <div className="brand-name">
            SCMS <span>· Smart Complaints</span>
          </div>
        </div>

        {/* Headline */}
        <h2 className="brand-headline">
          Every complaint<br />
          deserves an <em>answer.</em>
        </h2>
        <p className="brand-sub">
          Submit, track, and escalate institutional issues — from hostel
          maintenance to academic grievances — with full transparency.
        </p>

        {/* Mock ticket cards — shows the product in action */}
        <div className="ticket-widget">
          <div className="ticket-card">
            <div className="ticket-dot ticket-dot-green" />
            <div className="ticket-body">
              <div className="ticket-title">Wi-Fi outage in CS Lab B</div>
              <div className="ticket-meta">IT Services · 2h ago</div>
            </div>
            <span className="ticket-pill ticket-pill-green">Resolved</span>
          </div>

          <div className="ticket-card">
            <div className="ticket-dot ticket-dot-indigo" />
            <div className="ticket-body">
              <div className="ticket-title">Exam schedule conflict — Sem 4</div>
              <div className="ticket-meta">Academics · 1d ago</div>
            </div>
            <span className="ticket-pill ticket-pill-indigo">In Review</span>
          </div>

          <div className="ticket-card">
            <div className="ticket-dot ticket-dot-amber" />
            <div className="ticket-body">
              <div className="ticket-title">Hostel Block C water supply</div>
              <div className="ticket-meta">Infrastructure · 3d ago</div>
            </div>
            <span className="ticket-pill ticket-pill-amber">In Progress</span>
          </div>
        </div>
      </div>

      <div className="brand-footer">
        Designed for GEC Wayanad
      </div>
    </div>
  )
}
