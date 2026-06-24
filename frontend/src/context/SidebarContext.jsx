import React, { createContext, useContext, useState, useCallback, useEffect } from 'react'

/*
  SidebarContext — mobile navigation drawer open/closed state.

  MENTOR NOTE — fixing a real mobile UX bug found during the v2.0 review:
  v1.3's global.css hid the sidebar entirely below ~680px with NO
  replacement navigation — once hidden, a phone user had literally no way
  to navigate between Dashboard / Complaints / Reports / etc. The v1.3
  report's accessibility/UX findings ("no mobile responsiveness") undersold
  how broken this actually was: it wasn't just unpolished, navigation was
  unusable on a phone.

  This context is intentionally separate from AuthContext — it has nothing
  to do with authentication, and keeping it separate avoids re-rendering
  every auth-consuming component just because the drawer opened.
*/
const SidebarContext = createContext(null)

export function SidebarProvider({ children }) {
  const [open, setOpen] = useState(false)

  const toggle = useCallback(() => setOpen(o => !o), [])
  const close  = useCallback(() => setOpen(false), [])

  // Closing on route change is handled by each page's own NavLink onClick
  // (see Sidebar.jsx) rather than here, to avoid a router dependency in
  // this lightweight context.

  // Lock body scroll while the mobile drawer is open.
  useEffect(() => {
    document.body.style.overflow = open ? 'hidden' : ''
    return () => { document.body.style.overflow = '' }
  }, [open])

  // Close on Escape — basic keyboard accessibility for the drawer.
  useEffect(() => {
    if (!open) return
    const onKeyDown = (e) => { if (e.key === 'Escape') close() }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [open, close])

  return (
    <SidebarContext.Provider value={{ open, toggle, close }}>
      {children}
    </SidebarContext.Provider>
  )
}

export function useSidebar() {
  const ctx = useContext(SidebarContext)
  if (!ctx) throw new Error('useSidebar must be inside <SidebarProvider>')
  return ctx
}
