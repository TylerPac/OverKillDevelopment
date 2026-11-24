import { callJson, callProtectedText } from './apiClient';

const BASE = '/shop/admin/universal';
const JSON_HEADERS = { 'Content-Type': 'application/json' };

export const getPlatform = () => callJson(`${BASE}/platform`);

export const listMapLinkPlayers = (state = '') =>
  callJson(`${BASE}/maplink/players${state ? `?state=${encodeURIComponent(state)}` : ''}`);

export const getMapLinkPlayer = (steamId) => callJson(`${BASE}/maplink/players/${steamId}`);

// The saved character as the exact JSON text, so nothing in it is rounded or reformatted.
export const getMapLinkStateText = (steamId, stateId) =>
  callProtectedText(`${BASE}/maplink/players/${steamId}/states/${stateId}`);

export const restoreMapLinkState = (steamId, stateId, serverId) =>
  callJson(`${BASE}/maplink/players/${steamId}/states/${stateId}/restore`, {
    method: 'POST',
    headers: JSON_HEADERS,
    body: JSON.stringify({ serverId }),
  });

export const listGameServers = () => callJson(`${BASE}/servers`);

export const registerGameServer = (serverId, apiKey) =>
  callJson(`${BASE}/servers`, {
    method: 'POST',
    headers: JSON_HEADERS,
    body: JSON.stringify({ serverId, apiKey }),
  });

export const setGameServerEnabled = (serverId, enabled) =>
  callJson(`${BASE}/servers/${encodeURIComponent(serverId)}`, {
    method: 'PATCH',
    headers: JSON_HEADERS,
    body: JSON.stringify({ enabled }),
  });
