import { useCallback, useEffect, useRef, useState } from 'react';
import {
  getDownloadsOverview,
  makeVersionCurrent,
  saveDownloadFile,
  saveProductDownload,
  uploadDownloadFile,
} from '../services/downloadsService';

// Shared files (Core, API, each mod) are uploaded once. Each product chooses which files its one download zip
// contains, and the download service rebuilds that zip whenever one of them gets a new version.

const MESSAGES = {
  downloads_not_configured: 'The download service is not set up for this site yet.',
  downloads_unavailable: 'The download service is unreachable. Try again shortly.',
  login_unavailable: 'Admin access could not be checked because the sign-in service is unreachable.',
  forbidden: 'Your account is not an admin.',
  invalid_key: 'IDs may only use lowercase letters, numbers and dashes.',
  invalid_display_name: 'Give the file a name (max 128 characters).',
  invalid_file_name: 'File names may use letters, numbers, spaces and @ . _ ( ) + - only.',
  invalid_size: 'That file is empty or larger than allowed.',
  insufficient_storage: 'The server is short of disk space for this upload.',
  invalid_zip: 'That .zip file could not be opened.',
  unsafe_zip_entry: 'That .zip contains a path that would extract outside its folder.',
  empty_zip: 'That .zip is empty.',
  sha256_mismatch: 'The file changed while uploading. Try again.',
  size_mismatch: 'The upload was incomplete. Try again.',
  upload_expired: 'The upload took more than a day and expired. Start it again.',
  invalid_github_url: 'Use a https://github.com/owner/repo link.',
  invalid_bundle_artifacts: 'Choose at least one file (max 20).',
  bundle_file_must_be_zip: 'The download name must end in .zip.',
  network_error: 'Lost connection while uploading. Pick the same file again to resume.',
  cancelled: 'Upload cancelled. Pick the same file again to resume.',
};

const describe = (error) => MESSAGES[error?.code || error?.message] || `Something went wrong (${error?.code || error?.message}).`;

function formatBytes(bytes) {
  if (bytes == null) return '—';
  const units = ['B', 'KB', 'MB', 'GB'];
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit += 1;
  }
  return `${value.toFixed(unit === 0 ? 0 : 1)} ${units[unit]}`;
}

const formatDate = (value) => (value ? new Date(value).toLocaleString() : '—');

const STATE_LABEL = {
  READY: ['ok', 'Ready'],
  BUILDING: ['live', 'Building'],
  STALE: ['live', 'Rebuilding'],
  INCOMPLETE: ['off', 'Missing files'],
  FAILED: ['off', 'Build failed'],
};

function FileRow({ file, onChanged }) {
  const inputRef = useRef(null);
  const controllerRef = useRef(null);
  const [progress, setProgress] = useState(null);
  const [error, setError] = useState('');
  const [showHistory, setShowHistory] = useState(false);
  const current = file.versions?.find((v) => v.id === file.currentVersionId);
  const uploading = progress && progress.phase !== 'done';

  async function handlePick(event) {
    const picked = event.target.files?.[0];
    event.target.value = '';
    if (!picked) return;
    setError('');
    controllerRef.current = new AbortController();
    try {
      await uploadDownloadFile(file.key, picked, { onProgress: setProgress, signal: controllerRef.current.signal });
      await onChanged();
    } catch (err) {
      setError(describe(err));
    } finally {
      setProgress(null);
      controllerRef.current = null;
    }
  }

  async function handleMakeCurrent(versionId) {
    setError('');
    try {
      await makeVersionCurrent(file.key, versionId);
      await onChanged();
    } catch (err) {
      setError(describe(err));
    }
  }

  const percent = progress ? Math.floor((progress.sent / Math.max(progress.total, 1)) * 100) : 0;

  return (
    <>
      <tr>
        <td>
          <span className="code">{file.displayName}</span>
          <div className="dim">{file.key}</div>
        </td>
        <td>
          {current ? (
            <>
              {current.fileName} <span className="dim">· {formatBytes(current.sizeBytes)}</span>
              <div className="dim">{formatDate(current.createdAt)}{current.uploadedBy ? ` · ${current.uploadedBy}` : ''}</div>
            </>
          ) : (
            <span className="muted">Nothing uploaded yet</span>
          )}
          {progress && (
            <>
              <div className="progress" role="progressbar" aria-valuenow={percent} aria-valuemin={0} aria-valuemax={100}>
                <span style={{ width: `${percent}%` }} />
              </div>
              <div className="dim">
                {progress.phase === 'checking'
                  ? 'Checking the file…'
                  : `${formatBytes(progress.sent)} of ${formatBytes(progress.total)} (${percent}%)${progress.resumed ? ' · resumed' : ''}`}
              </div>
            </>
          )}
          {error && <p className="notice error">{error}</p>}
        </td>
        <td className="actions">
          <div className="button-row end">
            <input ref={inputRef} type="file" hidden onChange={handlePick} />
            {uploading ? (
              <button type="button" className="btn btn-sm" onClick={() => controllerRef.current?.abort()}>Cancel</button>
            ) : (
              <button type="button" className="btn btn-sm btn-primary" onClick={() => inputRef.current?.click()}>
                Upload new version
              </button>
            )}
            {file.versions?.length > 1 && (
              <button type="button" className="btn btn-sm" onClick={() => setShowHistory((s) => !s)}>
                {showHistory ? 'Hide history' : 'History'}
              </button>
            )}
          </div>
        </td>
      </tr>
      {showHistory && file.versions.map((version) => (
        <tr key={version.id}>
          <td className="dim">Version {version.id}</td>
          <td className="muted">
            {version.fileName} · {formatBytes(version.sizeBytes)} · {formatDate(version.createdAt)}
            {version.note ? ` · ${version.note}` : ''}
          </td>
          <td className="actions">
            {version.id === file.currentVersionId ? (
              <span className="status ok">Current</span>
            ) : (
              <button type="button" className="btn btn-sm" onClick={() => handleMakeCurrent(version.id)}>Make current</button>
            )}
          </td>
        </tr>
      ))}
    </>
  );
}

function AddFileForm({ onAdded }) {
  const [key, setKey] = useState('');
  const [name, setName] = useState('');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    setSaving(true);
    setError('');
    try {
      await saveDownloadFile(key.trim(), name.trim());
      setKey('');
      setName('');
      await onAdded();
    } catch (err) {
      setError(describe(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <form className="panel" onSubmit={handleSubmit}>
      <h3>Add a file</h3>
      <p className="muted">A file is uploaded once and can be part of several products, like Core or the API.</p>
      <div className="download-grid">
        <div className="field">
          <label className="label" htmlFor="download-file-key">ID</label>
          <input id="download-file-key" className="input-block" value={key} onChange={(e) => setKey(e.target.value)}
            placeholder="maplink" pattern="[a-z0-9][a-z0-9-]{0,63}" required />
        </div>
        <div className="field">
          <label className="label" htmlFor="download-file-name">Name</label>
          <input id="download-file-name" className="input-block" value={name} onChange={(e) => setName(e.target.value)}
            placeholder="OverKill Map Link" maxLength={128} required />
        </div>
      </div>
      {error && <p className="notice error">{error}</p>}
      <button type="submit" className="btn btn-primary" disabled={saving}>{saving ? 'Adding…' : 'Add file'}</button>
    </form>
  );
}

function ProductRow({ product, files, bundle, onSaved }) {
  const [chosen, setChosen] = useState(bundle?.artifacts || []);
  const [fileName, setFileName] = useState(bundle?.fileName || `${product.id}.zip`);
  const [githubUrl, setGithubUrl] = useState(product.githubUrl || '');
  const [error, setError] = useState('');
  const [saved, setSaved] = useState(false);
  const [saving, setSaving] = useState(false);
  const [label, labelText] = STATE_LABEL[bundle?.state] || ['', 'Not set up'];

  function toggle(key) {
    setSaved(false);
    // The order chosen is the order in the zip; the first file wins if two contain the same path
    setChosen((list) => (list.includes(key) ? list.filter((k) => k !== key) : [...list, key]));
  }

  async function handleSave() {
    setSaving(true);
    setError('');
    setSaved(false);
    try {
      await saveProductDownload(product.id, { files: chosen, fileName: fileName.trim(), githubUrl: githubUrl.trim() });
      setSaved(true);
      await onSaved();
    } catch (err) {
      setError(describe(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="download-product">
      <div className="section-head">
        <h3>{product.name}</h3>
        <span className={`status ${label}`}>{labelText}</span>
      </div>
      {bundle?.current && (
        <p className="dim">
          Current download: {bundle.fileName} · {formatBytes(bundle.current.sizeBytes)} · built {formatDate(bundle.current.finishedAt)}
        </p>
      )}
      <div className="field">
        <span className="label">Files in this download (in order)</span>
        {files.length === 0 ? (
          <p className="muted">Add files above first.</p>
        ) : (
          <div className="file-choices">
            {[
              ...chosen.map((key) => files.find((f) => f.key === key)).filter(Boolean),
              ...files.filter((f) => !chosen.includes(f.key)),
            ].map((file) => (
              <button key={file.key} type="button" onClick={() => toggle(file.key)}
                className={chosen.includes(file.key) ? 'chip active' : 'chip'}>
                {chosen.includes(file.key) ? `${chosen.indexOf(file.key) + 1}. ` : ''}{file.displayName}
              </button>
            ))}
          </div>
        )}
      </div>
      <div className="download-grid">
        <div className="field">
          <label className="label" htmlFor={`dl-name-${product.id}`}>Download file name</label>
          <input id={`dl-name-${product.id}`} className="input-block" value={fileName}
            onChange={(e) => { setFileName(e.target.value); setSaved(false); }} />
        </div>
        <div className="field">
          <label className="label" htmlFor={`dl-github-${product.id}`}>GitHub link (shown on the product page)</label>
          <input id={`dl-github-${product.id}`} className="input-block" value={githubUrl} placeholder="https://github.com/owner/repo"
            onChange={(e) => { setGithubUrl(e.target.value); setSaved(false); }} />
        </div>
      </div>
      {error && <p className="notice error">{error}</p>}
      {saved && <p className="notice ok" role="status">Saved. The download rebuilds in the background.</p>}
      <button type="button" className="btn btn-primary btn-sm" onClick={handleSave} disabled={saving}>
        {saving ? 'Saving…' : 'Save'}
      </button>
    </div>
  );
}

export default function DownloadsAdmin() {
  const [overview, setOverview] = useState(null);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    try {
      const next = await getDownloadsOverview();
      setOverview(next);
      setError(next.error ? describe({ code: next.error }) : '');
    } catch (err) {
      setError(describe(err));
    }
  }, []);

  useEffect(() => {
    getDownloadsOverview()
      .then((next) => {
        setOverview(next);
        setError(next.error ? describe({ code: next.error }) : '');
      })
      .catch((err) => setError(describe(err)));
  }, []);

  // Bundles rebuild in the background; refresh while any is building
  const building = overview?.bundles?.some((b) => b.state === 'BUILDING' || b.state === 'STALE');
  useEffect(() => {
    if (!building) return undefined;
    const timer = setInterval(load, 3000);
    return () => clearInterval(timer);
  }, [building, load]);

  const files = overview?.files || [];
  const bundles = overview?.bundles || [];

  return (
    <section className="section">
      <div className="section-head">
        <h2>Downloads</h2>
        <p>Upload once · every product that includes a file picks it up</p>
      </div>
      {error && <p className="notice error">{error}</p>}
      {!overview && !error && <p className="notice">Loading…</p>}
      {overview && (
        <>
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>File</th>
                  <th>Current version</th>
                  <th className="actions" />
                </tr>
              </thead>
              <tbody>
                {files.length === 0 ? (
                  <tr><td colSpan={3} className="muted">No files yet.</td></tr>
                ) : files.map((file) => <FileRow key={file.key} file={file} onChanged={load} />)}
              </tbody>
            </table>
          </div>
          <p className="dim">
            Large files are sent in pieces. If an upload stops, pick the same file again and it carries on where it left off.
          </p>

          <div className="split">
            <div className="panel">
              <h3>Products</h3>
              {overview.products.map((product) => (
                <ProductRow key={product.id} product={product} files={files}
                  bundle={bundles.find((b) => b.key === product.id)} onSaved={load} />
              ))}
            </div>
            <AddFileForm onAdded={load} />
          </div>
        </>
      )}
    </section>
  );
}
