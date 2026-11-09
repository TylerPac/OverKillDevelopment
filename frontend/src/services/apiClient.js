export const apiBase =
  (typeof import.meta !== 'undefined' && import.meta.env && import.meta.env.VITE_API_BASE_URL) ||
  'http://localhost:8080';

export const SESSION_EXPIRED_EVENT = 'overkill-session-expired';

const SAFE_METHODS = ['GET', 'HEAD', 'OPTIONS'];

function csrfToken() {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : '';
}

// The browser stays signed in with an httpOnly session cookie; state-changing requests also send the CSRF token.
export async function apiFetch(path, options = {}) {
  const method = (options.method || 'GET').toUpperCase();
  const headers = new Headers(options.headers);
  const csrf = csrfToken();
  if (csrf && !SAFE_METHODS.includes(method)) {
    headers.set('X-XSRF-TOKEN', csrf);
  }

  const response = await fetch(`${apiBase}${path}`, { ...options, headers, credentials: 'include' });
  if (response.status === 401 && path !== '/auth/me') {
    window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
  }
  return response;
}

export async function callJson(path, options = {}) {
  const response = await apiFetch(path, options);
  const contentType = response.headers.get('content-type') || '';
  const body = contentType.includes('application/json') ? await response.json() : await response.text();

  if (!response.ok) {
    const message = typeof body === 'string' ? body : body?.message || `Request failed (${response.status})`;
    throw new Error(message);
  }

  return body;
}

export async function callProtectedText(path) {
  const response = await apiFetch(path);

  const text = await response.text();
  if (!response.ok) {
    throw new Error(text || `Request failed (${response.status})`);
  }

  return text;
}

export async function getProductDownloadLink(productId) {
  const response = await apiFetch(`/shop/download-link/${encodeURIComponent(productId)}`);

  if (response.status === 404) return null;

  if (!response.ok) {
    const message = await response.text();
    throw new Error(message || `Request failed (${response.status})`);
  }

  const data = await response.json();
  return data.url || null;
}

export async function issueDownloadToken(productId) {
  const response = await apiFetch(`/shop/download-token/${encodeURIComponent(productId)}`, {
    method: 'POST',
  });

  if (!response.ok) {
    const message = await response.text();
    throw new Error(message || `Request failed (${response.status})`);
  }

  const data = await response.json();
  return data.token;
}

export async function markProductRelease(productId) {
  const response = await apiFetch(`/shop/admin/product-release/${encodeURIComponent(productId)}`, {
    method: 'POST',
  });

  if (!response.ok) {
    const message = await response.text();
    throw new Error(message || `Request failed (${response.status})`);
  }

  return response.json();
}

export async function downloadProductBlob(productId) {
  const response = await apiFetch(`/shop/download/${encodeURIComponent(productId)}`);

  if (!response.ok) {
    const message = await response.text();
    throw new Error(message || `Download failed (${response.status})`);
  }

  const blob = await response.blob();
  const disposition = response.headers.get('content-disposition') || '';
  const fileNameMatch = disposition.match(/filename="?([^";]+)"?/i);
  const fileName = fileNameMatch ? fileNameMatch[1] : `${productId}.zip`;

  return { blob, fileName };
}
