import { apiBase, callJson } from './apiClient';

function authHeaders(token) {
  return { Authorization: `Bearer ${token}` };
}

export async function listTierZoneMaps(token) {
  return callJson('/tier-zones', { headers: authHeaders(token) });
}

export async function getTierZoneMap(token, mapName) {
  return callJson(`/tier-zones/${encodeURIComponent(mapName)}`, { headers: authHeaders(token) });
}

export async function saveTierZoneMap(token, mapName, polygonsJson) {
  return callJson(`/tier-zones/${encodeURIComponent(mapName)}`, {
    method: 'PUT',
    headers: { ...authHeaders(token), 'Content-Type': 'application/json' },
    body: JSON.stringify({ polygonsJson }),
  });
}

export async function deleteTierZoneMap(token, mapName) {
  return callJson(`/tier-zones/${encodeURIComponent(mapName)}`, {
    method: 'DELETE',
    headers: authHeaders(token),
  });
}

export function downloadTierZoneMapUrl(mapName) {
  return `${apiBase}/tier-zones/${encodeURIComponent(mapName)}/download`;
}
