// Sends a file to the download service in fixed-size chunks, straight from the browser.
// Each chunk is retried a few times, and an upload interrupted by a dropped connection or a page reload resumes
// from the chunks the service already has. Nothing here depends on the page, so it also runs under Node.
//
// start() opens the upload (through Overkill's backend, which checks the admin) and returns
// { uploadId, uploadToken, uploadUrl, chunkSizeBytes, chunkCount, expiresAt }.

const RESUME_PREFIX = 'ok-upload:';

export class UploadError extends Error {
  constructor(code) {
    super(code);
    this.code = code;
  }
}

function browserStorage() {
  try {
    return typeof localStorage !== 'undefined' ? localStorage : null;
  } catch {
    return null;
  }
}

function sleep(ms, signal) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(resolve, ms);
    signal?.addEventListener('abort', () => {
      clearTimeout(timer);
      reject(new UploadError('cancelled'));
    }, { once: true });
  });
}

async function errorCode(response) {
  try {
    const body = await response.json();
    return body?.error || `http_${response.status}`;
  } catch {
    return `http_${response.status}`;
  }
}

/** Identifies the same file picked again for the same download, so an interrupted upload can resume. */
export function resumeKey(target, file) {
  return `${RESUME_PREFIX}${target}:${file.name}:${file.size}:${file.lastModified ?? 0}`;
}

export async function uploadInChunks({
  file,
  target,
  start,
  onProgress = () => {},
  signal,
  storage = browserStorage(),
  fetchImpl = (...args) => fetch(...args),
  retries = 3,
  pollMs = 1500,
}) {
  const key = resumeKey(target, file);
  const auth = (session) => ({ Authorization: `Bearer ${session.uploadToken}` });

  async function status(session) {
    const response = await fetchImpl(session.uploadUrl, { headers: auth(session), signal });
    if (!response.ok) throw new UploadError(await errorCode(response));
    return response.json();
  }

  // Resume a session for this exact file if one is still open
  let session = null;
  let current = null;
  const saved = storage?.getItem(key);
  if (saved) {
    try {
      const candidate = JSON.parse(saved);
      if (new Date(candidate.expiresAt).getTime() > Date.now() + 5 * 60 * 1000) {
        const found = await status(candidate);
        if (found.status === 'OPEN') {
          session = candidate;
          current = found;
        }
      }
    } catch {
      // Expired, finished or unknown: start a new one
    }
    if (!session) storage?.removeItem(key);
  }
  if (!session) {
    session = await start();
    storage?.setItem(key, JSON.stringify(session));
    current = { receivedChunks: [] };
  }

  const chunkSize = session.chunkSizeBytes;
  const received = new Set(current.receivedChunks || []);
  const sizeOf = (index) => Math.min(file.size, (index + 1) * chunkSize) - index * chunkSize;
  let sent = [...received].reduce((total, index) => total + sizeOf(index), 0);
  onProgress({ phase: 'uploading', sent, total: file.size, resumed: received.size > 0 });

  for (let index = 0; index < session.chunkCount; index += 1) {
    if (received.has(index)) continue;
    const chunk = file.slice(index * chunkSize, index * chunkSize + sizeOf(index));
    for (let attempt = 0; ; attempt += 1) {
      if (signal?.aborted) throw new UploadError('cancelled');
      let response;
      try {
        response = await fetchImpl(`${session.uploadUrl}/chunks/${index}`, {
          method: 'PUT',
          headers: { ...auth(session), 'Content-Type': 'application/octet-stream' },
          body: chunk,
          signal,
        });
      } catch {
        if (signal?.aborted) throw new UploadError('cancelled');
        response = null;
      }
      if (response?.ok) break;
      const code = response ? await errorCode(response) : 'network_error';
      // These will not get better by trying again
      if (response && [400, 401, 409, 410].includes(response.status)) {
        storage?.removeItem(key);
        throw new UploadError(code);
      }
      if (attempt >= retries) throw new UploadError(code);
      await sleep(1000 * 2 ** attempt, signal);
    }
    sent += sizeOf(index);
    onProgress({ phase: 'uploading', sent, total: file.size });
  }

  onProgress({ phase: 'checking', sent: file.size, total: file.size });
  const completed = await fetchImpl(`${session.uploadUrl}/complete`, { method: 'POST', headers: auth(session), signal });
  if (!completed.ok) throw new UploadError(await errorCode(completed));

  // The service joins and checks the file in the background
  for (;;) {
    const result = await status(session);
    if (result.status === 'COMPLETE') {
      storage?.removeItem(key);
      onProgress({ phase: 'done', sent: file.size, total: file.size });
      return result;
    }
    if (result.status === 'FAILED' || result.status === 'EXPIRED') {
      storage?.removeItem(key);
      throw new UploadError(result.error || 'upload_failed');
    }
    await sleep(pollMs, signal);
  }
}
