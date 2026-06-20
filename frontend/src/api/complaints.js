import api from './axios'

/*
  complaints.js — all API calls for the complaint module.

  MENTOR NOTE — why a dedicated API module per feature?
  If tomorrow the backend URL changes from /api/complaints to /api/v2/complaints,
  you change it in ONE file. If you scatter api.get('/api/complaints') calls
  across 5 components, you have to hunt them all down. This is the DRY principle
  applied to API layer code.

  All functions return the data payload directly (unwrapping response.data)
  so callers don't have to write `.data` everywhere.
*/

export const complaintsApi = {

  // GET /api/complaints — list (student: own; admin: all)
  list: () =>
    api.get('/complaints').then(r => r.data),

  // GET /api/complaints/stats — dashboard KPIs
  stats: () =>
    api.get('/complaints/stats').then(r => r.data),

  // GET /api/complaints/:id — single complaint
  get: (id) =>
    api.get(`/complaints/${id}`).then(r => r.data),

  // POST /api/complaints — create new complaint
  create: (payload) =>
    api.post('/complaints', payload).then(r => r.data),

  // PATCH /api/complaints/:id/status — update status (admin only)
  updateStatus: (id, payload) =>
    api.patch(`/complaints/${id}/status`, payload).then(r => r.data),

  // DELETE /api/complaints/:id — soft-delete
  delete: (id) =>
    api.delete(`/complaints/${id}`).then(r => r.data),
}
