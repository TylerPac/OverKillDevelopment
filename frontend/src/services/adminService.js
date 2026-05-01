import { callJson } from './apiClient';

function authHeaders(token) {
  return { Authorization: `Bearer ${token}` };
}

export function listAccessCodes(token) {
  return callJson('/shop/admin/codes', {
    headers: authHeaders(token),
  });
}

export function createFullUnlockCode(token, code) {
  return callJson('/shop/admin/codes/full-unlock', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders(token) },
    body: JSON.stringify(code ? { code } : {}),
  });
}

export function createSpecificCode(token, code, productIds) {
  return callJson('/shop/admin/codes/create', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders(token) },
    body: JSON.stringify({ ...(code ? { code } : {}), productIds }),
  });
}

export function revokeCode(token, id) {
  return callJson(`/shop/admin/codes/${id}/revoke`, {
    method: 'POST',
    headers: authHeaders(token),
  });
}

export function deleteCode(token, id) {
  return callJson(`/shop/admin/codes/${id}`, {
    method: 'DELETE',
    headers: authHeaders(token),
  });
}
