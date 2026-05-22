export const apiBase =
  (typeof import.meta !== 'undefined' && import.meta.env && import.meta.env.VITE_API_BASE_URL) ||
  'http://localhost:8080';

export async function callJson(path, options = {}) {
  const response = await fetch(`${apiBase}${path}`, options);
  const contentType = response.headers.get('content-type') || '';
  const body = contentType.includes('application/json') ? await response.json() : await response.text();

  if (!response.ok) {
    const message = typeof body === 'string' ? body : body?.message || `Request failed (${response.status})`;
    throw new Error(message);
  }

  return body;
}

export async function callProtectedText(path, token) {
  const response = await fetch(`${apiBase}${path}`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });

  const text = await response.text();
  if (!response.ok) {
    throw new Error(text || `Request failed (${response.status})`);
  }

  return text;
}

export async function getProductDownloadLink(productId, token) {
  const response = await fetch(`${apiBase}/shop/download-link/${encodeURIComponent(productId)}`, {
    headers: { Authorization: `Bearer ${token}` },
  });

  if (response.status === 404) return null;

  if (!response.ok) {
    const message = await response.text();
    throw new Error(message || `Request failed (${response.status})`);
  }

  const data = await response.json();
  return data.url || null;
}

export async function issueDownloadToken(productId, token) {
  const response = await fetch(`${apiBase}/shop/download-token/${encodeURIComponent(productId)}`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  });

  if (!response.ok) {
    const message = await response.text();
    throw new Error(message || `Request failed (${response.status})`);
  }

  const data = await response.json();
  return data.token;
}

export async function markProductRelease(productId, token) {
  const response = await fetch(`${apiBase}/shop/admin/product-release/${encodeURIComponent(productId)}`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  });

  if (!response.ok) {
    const message = await response.text();
    throw new Error(message || `Request failed (${response.status})`);
  }

  return response.json();
}

export async function downloadProductBlob(productId, token) {
  const response = await fetch(`${apiBase}/shop/download/${encodeURIComponent(productId)}`, {
    method: 'GET',
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });

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
