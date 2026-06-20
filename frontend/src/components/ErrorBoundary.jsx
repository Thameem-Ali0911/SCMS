import React from 'react'

/*
  ErrorBoundary — catches JavaScript runtime errors anywhere in the child
  component tree, and shows a fallback UI instead of a blank screen.

  MENTOR NOTE — why class component?
  Error boundaries MUST be class components. React's error boundary API
  (componentDidCatch + getDerivedStateFromError) is not available as hooks.
  This is one of the few remaining valid reasons to write a class component
  in modern React. Every other component in SCMS is a function component.

  MENTOR NOTE — what errors does it catch?
  - Rendering errors (e.g. accessing .name on null)
  - Errors in lifecycle methods
  - Errors in constructors of child components

  What it does NOT catch:
  - Errors in event handlers (use try/catch there)
  - Async errors (use try/catch in async functions)
  - Errors in the boundary itself

  MENTOR NOTE — placement:
  Mount it at the top level in App.jsx wrapping all routes. You can also
  use it more granularly around specific sections (e.g. wrap the chart
  section in AdminReports so a recharts crash doesn't kill the whole page).

  Usage:
    <ErrorBoundary>
      <App />
    </ErrorBoundary>

    or with a custom fallback:
    <ErrorBoundary fallback={<p>Charts failed to load.</p>}>
      <AdminReports />
    </ErrorBoundary>
*/
export default class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props)
    this.state = { hasError: false, error: null }
  }

  static getDerivedStateFromError(error) {
    // Update state so the next render shows the fallback UI.
    return { hasError: true, error }
  }

  componentDidCatch(error, info) {
    // Log the error for debugging. In production, send to an error tracking
    // service like Sentry: Sentry.captureException(error, { extra: info })
    console.error('[ErrorBoundary] Caught error:', error)
    console.error('[ErrorBoundary] Component stack:', info.componentStack)
  }

  handleReset = () => {
    this.setState({ hasError: false, error: null })
  }

  render() {
    if (this.state.hasError) {
      // Custom fallback prop takes priority
      if (this.props.fallback) return this.props.fallback

      return (
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          minHeight: '100vh',
          padding: '2rem',
          background: '#F8FAFC',
          fontFamily: 'Inter, system-ui, sans-serif',
          textAlign: 'center',
        }}>
          {/* Error icon */}
          <div style={{
            width: 64, height: 64, borderRadius: '50%',
            background: '#FEE2E2',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            marginBottom: '1.5rem',
          }}>
            <svg viewBox="0 0 24 24" fill="none" stroke="#DC2626"
                 strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"
                 style={{ width: 32, height: 32 }}>
              <circle cx="12" cy="12" r="10"/>
              <line x1="12" y1="8" x2="12" y2="12"/>
              <line x1="12" y1="16" x2="12.01" y2="16"/>
            </svg>
          </div>

          <h2 style={{ fontSize: '1.25rem', fontWeight: 700,
                       color: '#0F1923', marginBottom: '0.5rem' }}>
            Something went wrong
          </h2>
          <p style={{ color: '#64748B', fontSize: '0.9rem',
                      maxWidth: 420, marginBottom: '1.5rem', lineHeight: 1.6 }}>
            An unexpected error occurred in this part of the application.
            You can try refreshing or returning to the dashboard.
          </p>

          <div style={{ display: 'flex', gap: '0.75rem' }}>
            <button
              onClick={this.handleReset}
              style={{
                padding: '0.55rem 1.1rem', borderRadius: 6,
                background: '#4F46E5', color: '#fff', border: 'none',
                fontWeight: 600, fontSize: '0.875rem', cursor: 'pointer',
              }}
            >
              Try again
            </button>
            <a
              href="/dashboard"
              style={{
                padding: '0.55rem 1.1rem', borderRadius: 6,
                background: '#F1F5F9', color: '#334155', border: '1px solid #E2E8F0',
                fontWeight: 600, fontSize: '0.875rem', textDecoration: 'none',
              }}
            >
              Go to Dashboard
            </a>
          </div>

          {/* Dev-only: show the error message */}
          {import.meta.env.DEV && this.state.error && (
            <details style={{
              marginTop: '2rem', textAlign: 'left', width: '100%',
              maxWidth: 600, background: '#fff', border: '1px solid #E2E8F0',
              borderRadius: 8, padding: '1rem',
            }}>
              <summary style={{ cursor: 'pointer', fontSize: '0.8rem',
                                color: '#64748B', fontWeight: 600 }}>
                Error details (dev only)
              </summary>
              <pre style={{
                fontSize: '0.75rem', color: '#DC2626', marginTop: '0.75rem',
                whiteSpace: 'pre-wrap', wordBreak: 'break-all',
              }}>
                {this.state.error.toString()}
              </pre>
            </details>
          )}
        </div>
      )
    }

    return this.props.children
  }
}
