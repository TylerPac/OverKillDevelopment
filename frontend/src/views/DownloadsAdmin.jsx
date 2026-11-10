import { useCallback, useEffect, useRef, useState } from 'react';
import {
  deleteDownloadPart,
  getDownloadsOverview,
  makeVersionCurrent,
  saveDownloadFile,
  saveProductDownload,
  uploadDownloadFile,
} from '../services/downloadsService';

// Each file (Core, API, each mod) has a client part (what players load, and what may go on the Steam Workshop)
// and/or a server part (what only the game server loads). Each part is uploaded once. Every product gets two
// downloads, client and server, made of that part of each file it includes, rebuilt when any part changes.

const PARTS = ['client', 'server'];
const PART_LABEL = { client: 'Client', server: 'Server', single: 'Old single file' };

const MESSAGES = {
  downloads_not_configured: 'The download service is not set up for this site yet.',
  downloads_unavailable: 'The download service is unreachable. Try again shortly.',
  login_unavailable: 'Admin access could not be checked because the sign-in service is unreachable.',
  forbidden: 'Your account is not an admin.',
  invalid_key: 'IDs may only use lowercase letters, numbers and dashes, and cannot end in -client or -server.',
  invalid_parts: 'Choose at least one part: client or server.',
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
  no_parts: 'None of the chosen files has a client or server part yet.',
  artifact_in_use: 'A product still includes this. Remove it from the product first, then delete it.',
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
  INCOMPLETE: ['off', 'Missing uploads'],
  FAILED: ['off', 'Build failed'],
};

/** "core-client" and "core-server" become one file "core" with two parts; anything else is an old single file. */
function groupFiles(files) {
  const groups = new Map();
  for (const file of files) {
    const match = file.key.match(/^(.*)-(client|server)$/);
    const base = match ? match[1] : file.key;
    const part = match ? match[2] : 'single';
    if (!groups.has(base)) {
      groups.set(base, { key: base, displayName: file.displayName.replace(/ \((Client|Server)\)$/, ''), parts: {} });
    }
    groups.get(base).parts[part] = file;
    // The parts carry the current name; an old single file may have an earlier one
    if (part !== 'single') {
      groups.get(base).displayName = file.displayName.replace(/ \((Client|Server)\)$/, '');
    }
  }
  return [...groups.values()];
}

function PartRow({ part, file, onChanged }) {
  const inputRef = useRef(null);
  const controllerRef = useRef(null);
  const [progress, setProgress] = useState(null);
  const [error, setError] = useState('');
  const [showHistory, setShowHistory] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
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

  async function handleDelete() {
    setError('');
    try {
      await deleteDownloadPart(file.key);
      await onChanged();
    } catch (err) {
      setError(describe(err));
      setConfirmDelete(false);
    }
  }

  const percent = progress ? Math.floor((progress.sent / Math.max(progress.total, 1)) * 100) : 0;

  return (
    <>
      <tr>
        <td><span className={part === 'single' ? 'status off' : 'status'}>{PART_LABEL[part]}</span></td>
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
              <>
                {part !== 'single' && (
                  <button type="button" className="btn btn-sm btn-primary" onClick={() => inputRef.current?.click()}>
                    Upload {PART_LABEL[part].toLowerCase()}
                  </button>
                )}
                {file.versions?.length > 1 && (
                  <button type="button" className="btn btn-sm" onClick={() => setShowHistory((s) => !s)}>
                    {showHistory ? 'Hide history' : 'History'}
                  </button>
                )}
                {confirmDelete ? (
                  <>
                    <button type="button" className="btn btn-sm btn-primary" onClick={handleDelete}>Delete for good</button>
                    <button type="button" className="btn btn-sm" onClick={() => setConfirmDelete(false)}>Keep</button>
                  </>
                ) : (
                  <button type="button" className="btn btn-sm" onClick={() => setConfirmDelete(true)}>Delete</button>
                )}
              </>
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

function FileGroup({ group, onChanged }) {
  const [error, setError] = useState('');
  const missing = PARTS.filter((part) => !group.parts[part]);
  const isOld = !group.parts.client && !group.parts.server;

  async function addPart(part) {
    setError('');
    try {
      await saveDownloadFile(group.key, group.displayName, [part]);
      await onChanged();
    } catch (err) {
      setError(describe(err));
    }
  }

  return (
    <>
      <tr className="group-row">
        <td colSpan={2}>
          <span className="code">{group.displayName}</span> <span className="dim">{group.key}</span>
          {isOld && <span className="dim"> · from before client and server parts; delete it once it is not needed</span>}
          {error && <p className="notice error">{error}</p>}
        </td>
        <td className="actions">
          {!isOld && missing.length > 0 && (
            <div className="button-row end">
              {missing.map((part) => (
                <button key={part} type="button" className="btn btn-sm" onClick={() => addPart(part)}>
                  + {PART_LABEL[part]} part
                </button>
              ))}
            </div>
          )}
        </td>
      </tr>
      {['client', 'server', 'single'].filter((part) => group.parts[part]).map((part) => (
        <PartRow key={part} part={part} file={group.parts[part]} onChanged={onChanged} />
      ))}
    </>
  );
}

function AddFileForm({ onAdded }) {
  const [key, setKey] = useState('');
  const [name, setName] = useState('');
  const [parts, setParts] = useState(['client', 'server']);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  const togglePart = (part) => setParts((list) => (list.includes(part) ? list.filter((p) => p !== part) : [...list, part]));

  async function handleSubmit(event) {
    event.preventDefault();
    setSaving(true);
    setError('');
    try {
      await saveDownloadFile(key.trim(), name.trim(), PARTS.filter((p) => parts.includes(p)));
      setKey('');
      setName('');
      setParts(['client', 'server']);
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
      <p className="muted">
        A file is uploaded once and can be part of several products, like Core or the API. Give it a client part
        (client assets players load) and/or a server part (server PBOs). If a part is several files, zip them.
      </p>
      <div className="download-grid">
        <div className="field">
          <label className="label" htmlFor="download-file-key">ID</label>
          <input id="download-file-key" className="input-block" value={key} onChange={(e) => setKey(e.target.value)}
            placeholder="maplink" pattern="[a-z0-9][a-z0-9-]{0,63}" required />
        </div>
        <div className="field">
          <label className="label" htmlFor="download-file-name">Name</label>
          <input id="download-file-name" className="input-block" value={name} onChange={(e) => setName(e.target.value)}
            placeholder="OverKill Map Link" maxLength={110} required />
        </div>
      </div>
      <div className="field">
        <span className="label">Parts</span>
        <div className="file-choices">
          {PARTS.map((part) => (
            <button key={part} type="button" onClick={() => togglePart(part)}
              className={parts.includes(part) ? 'chip active' : 'chip'} aria-pressed={parts.includes(part)}>
              {PART_LABEL[part]}
            </button>
          ))}
        </div>
      </div>
      {error && <p className="notice error">{error}</p>}
      <button type="submit" className="btn btn-primary" disabled={saving || parts.length === 0}>
        {saving ? 'Adding…' : 'Add file'}
      </button>
    </form>
  );
}

function ProductRow({ product, groups, bundles, onSaved }) {
  const clientBundle = bundles.find((b) => b.key === `${product.id}-client`);
  const serverBundle = bundles.find((b) => b.key === `${product.id}-server`);
  const stripPart = (key) => key.replace(/-(client|server)$/, '');
  const initial = [...new Set([...(clientBundle?.artifacts || []), ...(serverBundle?.artifacts || [])].map(stripPart))];
  const [chosen, setChosen] = useState(initial);
  const [baseName, setBaseName] = useState(clientBundle?.fileName?.replace(/-Client\.zip$/i, '')
    || serverBundle?.fileName?.replace(/-Server\.zip$/i, '') || product.id);
  const [githubUrl, setGithubUrl] = useState(product.githubUrl || '');
  const [error, setError] = useState('');
  const [saved, setSaved] = useState(false);
  const [saving, setSaving] = useState(false);
  const choosable = groups.filter((g) => g.parts.client || g.parts.server);

  function toggle(key) {
    setSaved(false);
    // The order chosen is the order in each zip; the first file wins if two contain the same path
    setChosen((list) => (list.includes(key) ? list.filter((k) => k !== key) : [...list, key]));
  }

  async function handleSave() {
    setSaving(true);
    setError('');
    setSaved(false);
    try {
      await saveProductDownload(product.id, { files: chosen, fileName: `${baseName.trim()}.zip`, githubUrl: githubUrl.trim() });
      setSaved(true);
      await onSaved();
    } catch (err) {
      setError(describe(err));
    } finally {
      setSaving(false);
    }
  }

  const badge = (part, bundle) => {
    const [cls, text] = bundle ? (STATE_LABEL[bundle.state] || ['', bundle.state]) : ['', 'Not set up'];
    return <span className={`status ${cls}`}>{PART_LABEL[part]}: {text}</span>;
  };

  return (
    <div className="download-product">
      <div className="section-head">
        <h3>{product.name}</h3>
        <div className="button-row">{badge('client', clientBundle)}{badge('server', serverBundle)}</div>
      </div>
      {[clientBundle, serverBundle].filter((b) => b?.current).map((b) => (
        <p key={b.key} className="dim">{b.fileName} · {formatBytes(b.current.sizeBytes)} · built {formatDate(b.current.finishedAt)}</p>
      ))}
      <div className="field">
        <span className="label">Files in these downloads (in order)</span>
        {choosable.length === 0 ? (
          <p className="muted">Add files with a client or server part first.</p>
        ) : (
          <div className="file-choices">
            {[
              ...chosen.map((key) => choosable.find((g) => g.key === key)).filter(Boolean),
              ...choosable.filter((g) => !chosen.includes(g.key)),
            ].map((group) => (
              <button key={group.key} type="button" onClick={() => toggle(group.key)}
                className={chosen.includes(group.key) ? 'chip active' : 'chip'}>
                {chosen.includes(group.key) ? `${chosen.indexOf(group.key) + 1}. ` : ''}{group.displayName}
              </button>
            ))}
          </div>
        )}
      </div>
      <div className="download-grid">
        <div className="field">
          <label className="label" htmlFor={`dl-name-${product.id}`}>Download name</label>
          <input id={`dl-name-${product.id}`} className="input-block" value={baseName}
            onChange={(e) => { setBaseName(e.target.value); setSaved(false); }} />
          <span className="dim">{baseName.trim() || product.id}-Client.zip and {baseName.trim() || product.id}-Server.zip</span>
        </div>
        <div className="field">
          <label className="label" htmlFor={`dl-github-${product.id}`}>GitHub link (shown on the product page)</label>
          <input id={`dl-github-${product.id}`} className="input-block" value={githubUrl} placeholder="https://github.com/owner/repo"
            onChange={(e) => { setGithubUrl(e.target.value); setSaved(false); }} />
        </div>
      </div>
      {error && <p className="notice error">{error}</p>}
      {saved && <p className="notice ok" role="status">Saved. The downloads rebuild in the background.</p>}
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

  // Downloads rebuild in the background; refresh while any is building
  const building = overview?.bundles?.some((b) => b.state === 'BUILDING' || b.state === 'STALE');
  useEffect(() => {
    if (!building) return undefined;
    const timer = setInterval(load, 3000);
    return () => clearInterval(timer);
  }, [building, load]);

  const groups = groupFiles(overview?.files || []);
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
                  <th>Part</th>
                  <th>Current version</th>
                  <th className="actions" />
                </tr>
              </thead>
              <tbody>
                {groups.length === 0 ? (
                  <tr><td colSpan={3} className="muted">No files yet.</td></tr>
                ) : groups.map((group) => <FileGroup key={group.key} group={group} onChanged={load} />)}
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
                <ProductRow key={product.id} product={product} groups={groups} bundles={bundles} onSaved={load} />
              ))}
            </div>
            <AddFileForm onAdded={load} />
          </div>
        </>
      )}
    </section>
  );
}
