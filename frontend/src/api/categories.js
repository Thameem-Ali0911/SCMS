import api from './axios'

/*
  categories.js — read-only category list for the complaint form.

  CHANGE in v2.0: replaces the hardcoded CATEGORIES array NewComplaint.jsx
  used to ship with. Categories are now backend-managed reference data (see
  CategoryService.java) — an admin can add/rename/deactivate a category from
  the Admin UI with no frontend code change at all.
*/

export const categoriesApi = {
  listActive: () =>
    api.get('/categories').then(r => r.data),
}
