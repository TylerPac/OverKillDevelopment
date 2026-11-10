import { callJson } from './apiClient';

export function listGrants() {
  return callJson('/shop/admin/grants');
}

export function grantProducts(steamId, productIds) {
  return callJson('/shop/admin/grants', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ steamId, productIds }),
  });
}

export function revokeGrant(id) {
  return callJson(`/shop/admin/grants/${id}/revoke`, {
    method: 'POST',
  });
}
