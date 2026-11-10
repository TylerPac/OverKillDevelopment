import { apiFetch } from './apiClient';
import { uploadInChunks } from './chunkedUpload';

// Admin page Downloads section and public product links. Errors carry the backend's code (for example
// bundle_not_ready) so the page can explain them.
async function call(path, options = {}) {
  const headers = new Headers(options.headers);
  if (options.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
  const response = await apiFetch(path, { ...options, headers });
  // The shop's older endpoints send their error code as plain text, the newer ones as {"error": code}
  const text = await response.text();
  let body = null;
  try {
    body = text ? JSON.parse(text) : null;
  } catch {
    body = null;
  }
  if (!response.ok) {
    const plain = /^[a-z_]{1,64}$/.test(text.trim()) ? text.trim() : null;
    throw new Error(body?.error || plain || `http_${response.status}`);
  }
  return body;
}

export function getDownloadsOverview() {
  return call('/shop/admin/downloads');
}

/** Creates or renames a file's client and/or server part. */
export function saveDownloadFile(key, displayName, parts) {
  return call(`/shop/admin/downloads/files/${encodeURIComponent(key)}`, {
    method: 'PUT',
    body: JSON.stringify({ displayName, parts }),
  });
}

/** Deletes one part (for example "core-server") with all its versions. */
export function deleteDownloadPart(key) {
  return call(`/shop/admin/downloads/files/${encodeURIComponent(key)}`, { method: 'DELETE' });
}

/** Which parts the signed-in buyer can download: { client: true, server: false }. */
export function getDownloadOptions(productId) {
  return call(`/shop/download-options/${encodeURIComponent(productId)}`);
}

export async function getPartDownloadLink(productId, part) {
  const body = await call(`/shop/download-link/${encodeURIComponent(productId)}?part=${encodeURIComponent(part)}`);
  return body.url;
}

export function makeVersionCurrent(key, versionId) {
  return call(`/shop/admin/downloads/files/${encodeURIComponent(key)}/current`, {
    method: 'POST',
    body: JSON.stringify({ versionId }),
  });
}

export function saveProductDownload(productId, { files, fileName, githubUrl }) {
  return call(`/shop/admin/downloads/products/${encodeURIComponent(productId)}`, {
    method: 'PUT',
    body: JSON.stringify({ files, fileName, githubUrl }),
  });
}

export async function getProductLinks() {
  const response = await apiFetch('/shop/product-links');
  return response.ok ? response.json() : {};
}

// Files up to this size get a SHA-256 check end to end; larger ones would need the whole file in memory
const HASH_LIMIT_BYTES = 100 * 1024 * 1024;

async function sha256Hex(file) {
  if (file.size > HASH_LIMIT_BYTES || !globalThis.crypto?.subtle) return null;
  const digest = await crypto.subtle.digest('SHA-256', await file.arrayBuffer());
  return Array.from(new Uint8Array(digest)).map((b) => b.toString(16).padStart(2, '0')).join('');
}

/** Uploads a new version of one file; resumes if the same file was interrupted before. */
export async function uploadDownloadFile(key, file, { note, onProgress, signal } = {}) {
  return uploadInChunks({
    file,
    target: key,
    signal,
    onProgress,
    start: async () => call(`/shop/admin/downloads/files/${encodeURIComponent(key)}/uploads`, {
      method: 'POST',
      body: JSON.stringify({ fileName: file.name, sizeBytes: file.size, sha256: await sha256Hex(file), note }),
    }),
  });
}
