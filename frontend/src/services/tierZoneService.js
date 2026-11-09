import { apiBase, callJson } from './apiClient';

export async function listTierZoneMaps() {
  return callJson('/tier-zones');
}

export async function getTierZoneMap(mapName) {
  return callJson(`/tier-zones/${encodeURIComponent(mapName)}`);
}

export async function saveTierZoneMap(mapName, polygonsJson) {
  return callJson(`/tier-zones/${encodeURIComponent(mapName)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ polygonsJson }),
  });
}

export async function deleteTierZoneMap(mapName) {
  return callJson(`/tier-zones/${encodeURIComponent(mapName)}`, {
    method: 'DELETE',
  });
}

export function downloadTierZoneMapUrl(mapName) {
  return `${apiBase}/tier-zones/${encodeURIComponent(mapName)}/download`;
}
