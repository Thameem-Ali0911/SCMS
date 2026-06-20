import api from './axios'

/*
  admin.js — all API calls for admin-only endpoints.

  MENTOR NOTE — separation from complaints.js:
  Just as the backend separates AdminController from ComplaintController,
  the frontend separates its API modules. If the admin API moves to a
  different base path (/api/v2/admin), you change it in one place.

  All functions unwrap .data so callers don't need to write `.data` everywhere.
*/

export const adminApi = {

    // ── User Management ──────────────────────────────────────────────────────

    // GET /api/admin/users — list all users
    listUsers: () =>
        api.get('/admin/users').then(r => r.data),

    // GET /api/admin/users/:id — single user detail
    getUser: (id) =>
        api.get(`/admin/users/${id}`).then(r => r.data),

    // PATCH /api/admin/users/:id/status — toggle active/inactive
    toggleUserStatus: (id, active) =>
        api.patch(`/admin/users/${id}/status`, { active }).then(r => r.data),

    // PATCH /api/admin/users/:id/role — change role to USER or ADMIN
    changeUserRole: (id, role) =>
        api.patch(`/admin/users/${id}/role`, { role }).then(r => r.data),

    // DELETE /api/admin/users/:id — soft-deactivate user
    deactivateUser: (id) =>
        api.delete(`/admin/users/${id}`).then(r => r.data),

    // ── Reports ──────────────────────────────────────────────────────────────

    // GET /api/admin/reports/summary — top-level KPI summary
    reportSummary: () =>
        api.get('/admin/reports/summary').then(r => r.data),

    // GET /api/admin/reports/by-status — count per status (for pie chart)
    reportByStatus: () =>
        api.get('/admin/reports/by-status').then(r => r.data),

    // GET /api/admin/reports/by-category — count per category (for bar chart)
    reportByCategory: () =>
        api.get('/admin/reports/by-category').then(r => r.data),

    // GET /api/admin/reports/by-user — top 10 complainants
    reportByUser: () =>
        api.get('/admin/reports/by-user').then(r => r.data),

    // GET /api/admin/reports/timeline — daily counts for last 30 days
    reportTimeline: () =>
        api.get('/admin/reports/timeline').then(r => r.data),
}
