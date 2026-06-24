import api from './axios'

/*
  admin.js — all API calls for admin-only endpoints.

  CHANGE in v2.0 (production hardening):
    • listUsers() now accepts { q, page, size } and the backend returns a
      PageResponse envelope (see complaints.js for the same fix and
      rationale).
    • changeUserRole() now accepts "USER", "STAFF", or "ADMIN" (was a
      USER/ADMIN-only toggle).
    • staffWorkload() is new — powers the Assignments page.
*/

export const adminApi = {

    // ── User Management ──────────────────────────────────────────────────────

    listUsers: (params = {}) =>
        api.get('/admin/users', { params }).then(r => r.data),

    getUser: (id) =>
        api.get(`/admin/users/${id}`).then(r => r.data),

    toggleUserStatus: (id, active) =>
        api.patch(`/admin/users/${id}/status`, { active }).then(r => r.data),

    changeUserRole: (id, role) =>
        api.patch(`/admin/users/${id}/role`, { role }).then(r => r.data),

    deactivateUser: (id) =>
        api.delete(`/admin/users/${id}`).then(r => r.data),

    // ── Staff assignment workflow ────────────────────────────────────────────

    staffWorkload: () =>
        api.get('/admin/staff').then(r => r.data),

    // ── Reports ──────────────────────────────────────────────────────────────

    reportSummary: () =>
        api.get('/admin/reports/summary').then(r => r.data),

    reportByStatus: () =>
        api.get('/admin/reports/by-status').then(r => r.data),

    reportByCategory: () =>
        api.get('/admin/reports/by-category').then(r => r.data),

    reportByUser: () =>
        api.get('/admin/reports/by-user').then(r => r.data),

    reportTimeline: () =>
        api.get('/admin/reports/timeline').then(r => r.data),

    // ── Category management ───────────────────────────────────────────────────

    listAllCategories: () =>
        api.get('/admin/categories').then(r => r.data),

    createCategory: (payload) =>
        api.post('/admin/categories', payload).then(r => r.data),

    updateCategory: (id, payload) =>
        api.put(`/admin/categories/${id}`, payload).then(r => r.data),

    setCategoryActive: (id, active) =>
        api.patch(`/admin/categories/${id}/active`, null, { params: { active } }).then(r => r.data),
}
