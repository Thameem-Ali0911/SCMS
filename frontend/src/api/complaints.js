import api from './axios'

/*
  complaints.js — all API calls for the complaint module.

  CHANGE in v2.0 (production hardening):
    • list()/queueMine()/queueUnassigned() now accept { page, size, status, q }
      and the backend returns a PageResponse envelope — { content, page,
      size, totalElements, totalPages, last } — instead of a bare array.
      Fixes the v1.3 "GET /api/complaints returns the entire table" finding.
    • selfAssign()/assign()/history() are new — power the STAFF assignment
      workflow and the previously API-less audit trail view.
*/

export const complaintsApi = {

  // GET /api/complaints — paginated; student: own, staff: assigned-to-them, admin: all
  list: (params = {}) =>
    api.get('/complaints', { params }).then(r => r.data),

  // GET /api/complaints/queue/unassigned — staff/admin pick-up queue
  queueUnassigned: (params = {}) =>
    api.get('/complaints/queue/unassigned', { params }).then(r => r.data),

  // GET /api/complaints/queue/mine — staff/admin's own assigned queue
  queueMine: (params = {}) =>
    api.get('/complaints/queue/mine', { params }).then(r => r.data),

  // GET /api/complaints/stats — dashboard KPIs
  stats: () =>
    api.get('/complaints/stats').then(r => r.data),

  // GET /api/complaints/:id — single complaint
  get: (id) =>
    api.get(`/complaints/${id}`).then(r => r.data),

  // GET /api/complaints/:id/history — version/audit trail
  history: (id) =>
    api.get(`/complaints/${id}/history`).then(r => r.data),

  // POST /api/complaints — create new complaint
  create: (payload) =>
    api.post('/complaints', payload).then(r => r.data),

  // PATCH /api/complaints/:id/status — update status (assignee or admin)
  updateStatus: (id, payload) =>
    api.patch(`/complaints/${id}/status`, payload).then(r => r.data),

  // POST /api/complaints/:id/assign/me — staff/admin self-assign from the queue
  selfAssign: (id) =>
    api.post(`/complaints/${id}/assign/me`).then(r => r.data),

  // POST /api/complaints/:id/assign — admin assigns to a specific staff/admin user
  assign: (id, assigneeId) =>
    api.post(`/complaints/${id}/assign`, { assigneeId }).then(r => r.data),

  // DELETE /api/complaints/:id — soft-delete
  delete: (id) =>
    api.delete(`/complaints/${id}`).then(r => r.data),
}
