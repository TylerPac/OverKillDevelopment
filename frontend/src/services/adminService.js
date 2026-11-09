import { callJson } from './apiClient';

export function listAccessCodes() {
  return callJson('/shop/admin/codes');
}

export function createFullUnlockCode(code) {
  return callJson('/shop/admin/codes/full-unlock', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(code ? { code } : {}),
  });
}

export function createSpecificCode(code, productIds) {
  return callJson('/shop/admin/codes/create', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ ...(code ? { code } : {}), productIds }),
  });
}

export function revokeCode(id) {
  return callJson(`/shop/admin/codes/${id}/revoke`, {
    method: 'POST',
  });
}

export function deleteCode(id) {
  return callJson(`/shop/admin/codes/${id}`, {
    method: 'DELETE',
  });
}
