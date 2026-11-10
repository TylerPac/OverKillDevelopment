import { useCallback, useEffect, useRef, useState } from 'react';
import {
  addDownloadEntry,
  deleteDownloadPart,
  getDownloadsOverview,
  makeVersionCurrent,
  refreshDownloadGroup,
  saveDownloadFile,
  saveProductDownload,
  uploadDownloadFile,
} from '../services/downloadsService';

// A file group (Core, API, each mod) has a client part (what players load, and what may go on the Steam Workshop)
// and/or a server part (what only the game server loads). Each part holds any number of files, placed in the
// standard mod layout under the group's mod folder: .pbo and .bisign in @Mod/addons, .bikey in @Mod/keys, the rest
// in @Mod; a .zip is merged as it is. Every product gets a client and a server download built from those parts.

const PARTS = ['client', 'server'];
const PART_LABEL = { client: 'Client', server: 'Server', single: 'Old single file' };
const KEY_PATTERN = /^(.+?)-(client|server)(?:--(.+))?$/;

const MESSAGES = {
  downloads_not_configured: 'The download service is not set up for this site yet.',
  downloads_unavailable: 'The download service is unreachable. Try again shortly.',
  login_unavailable: 'Admin access could not be checked because the sign-in service is unreachable.',
  forbidden: 'Your account is not an admin.',
  invalid_key: 'IDs may only use lowercase letters, numbers and dashes, and cannot end in -client or -server.',
  invalid_parts: 'Choose at least one part: client or server.',
  invalid_mod_folder: 'The mod folder may use letters, numbers and _ . - only, like @OverKillDevCore.',
  invalid_display_name: 'Give the file a name (max 110 characters).',
  invalid_file_name: 'File names may use letters, numbers, spaces and @ . _ ( ) + - only.',
  invalid_bundle_path: 'That mod folder cannot be used.',
  invalid_size: 'That file is empty or larger than allowed.',
  insufficient_storage: 'The server is short of disk space for this upload.',
  invalid_zip: 'That .zip file could not be opened.',
  unsafe_zip_entry: 'That .zip contains a path that would extract outside its folder.',
  empty_zip: 'That .zip is empty.',
  sha256_mismatch: 'The file changed while uploading. Try again.',
  size_mismatch: 'The upload was incomplete. Try again.',
  upload_expired: 'The upload took more than a day and expired. Start it again.',
  invalid_github_url: 'Use a https://github.com/owner/repo link.',
  invalid_bundle_artifacts: 'A download can hold at most 20 files.',
  bundle_file_must_be_zip: 'The download name must end in .zip.',
  no_parts: 'None of the chosen files has anything uploaded yet.',
  artifact_in_use: 'A product still includes this. Remove it from the product first, then delete it.',
  network_error: 'Lost connection while uploading. Pick the same files again to resume.',
  cancelled: 'Upload cancelled. Pick the same files again to resume.',
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

/** Where a file actually lands in the download, as the download service has it. */
function placeOf(file, fileName) {
  if ((fileName || '').toLowerCase().endsWith('.zip')) return 'merged as it is';
  return file.bundlePath || 'top of the zip';
}

/**
 * Artifacts grouped as file groups: "core-client" (the part, which may hold an older single file) and
 * "core-client--dankammo-pbo" (a file in that part). Keys without a part are old single files from before parts.
 */
function groupFiles(files, modFolders) {
  const groups = new Map();
  const groupFor = (key, name) => {
    if (!groups.has(key)) {
      groups.set(key, { key, displayName: name, modFolder: modFolders?.[key] || null, parts: {}, single: null });
    }
    return groups.get(key);
  };
  for (const file of files) {
    const match = file.key.match(KEY_PATTERN);
    if (!match) {
      groupFor(file.key, file.displayName).single = file;
      continue;
    }
    const [, base, part, entry] = match;
    const group = groupFor(base, base);
    group.parts[part] = group.parts[part] || { own: null, entries: [] };
    if (entry) {
      group.parts[part].entries.push(file);
    } else {
      group.parts[part].own = file;
      // The part carries the group's name, like "OverKill Dev Core (Client)"
      group.displayName = file.displayName.replace(/ \((Client|Server)\)$/, '');
    }
  }
  for (const group of groups.values()) {
    for (const part of Object.values(group.parts)) {
      part.entries.sort((a, b) => a.displayName.localeCompare(b.displayName));
    }
  }
  return [...groups.values()];
}

/** One stored file: its current version, where it lands, history and delete. */
function FileRow({ file, label, showPlace = true, onChanged }) {
  const [error, setError] = useState('');
  const [showHistory, setShowHistory] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const current = file.versions?.find((v) => v.id === file.currentVersionId);
  const place = showPlace && current ? placeOf(file, current.fileName) : null;

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

  return (
    <>
      <tr>
        <td className="file-cell">{label}</td>
        <td>
          {current ? (
            <>
              {current.fileName} <span className="dim">· {formatBytes(current.sizeBytes)}</span>
              <div className="dim">
                {place ? `→ ${place} · ` : ''}{formatDate(current.createdAt)}{current.uploadedBy ? ` · ${current.uploadedBy}` : ''}
              </div>
            </>
          ) : (
            <span className="muted">Upload not finished</span>
          )}
          {error && <p className="notice error">{error}</p>}
        </td>
        <td className="actions">
          <div className="button-row end">
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
          </div>
        </td>
      </tr>
      {showHistory && file.versions.map((version) => (
        <tr key={version.id}>
          <td className="dim file-cell">Version {version.id}</td>
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

/** A client or server part: add several files at once, then its files. */
function PartRows({ group, part, data, onChanged }) {
  const inputRef = useRef(null);
  const controllerRef = useRef(null);
  const [queue, setQueue] = useState(null);
  const [error, setError] = useState('');

  async function handlePick(event) {
    const picked = [...(event.target.files || [])];
    event.target.value = '';
    if (picked.length === 0) return;
    setError('');
    controllerRef.current = new AbortController();
    const signal = controllerRef.current.signal;
    try {
      for (let i = 0; i < picked.length; i += 1) {
        const file = picked[i];
        setQueue({ index: i, count: picked.length, name: file.name, progress: null });
        const entry = await addDownloadEntry(group.key, part, file.name);
        await uploadDownloadFile(entry.key, file, {
          signal,
          onProgress: (progress) => setQueue({ index: i, count: picked.length, name: file.name, progress }),
        });
      }
    } catch (err) {
      setError(describe(err));
    } finally {
      // Products pick up whatever finished, even if a later file failed
      try {
        await refreshDownloadGroup(group.key);
      } catch (err) {
        setError(describe(err));
      }
      setQueue(null);
      controllerRef.current = null;
      await onChanged();
    }
  }

  const progress = queue?.progress;
  const percent = progress ? Math.floor((progress.sent / Math.max(progress.total, 1)) * 100) : 0;

  return (
    <>
      <tr>
        <td><span className="status">{PART_LABEL[part]}</span></td>
        <td>
          {queue ? (
            <>
              <div>Uploading {queue.name} ({queue.index + 1} of {queue.count})</div>
              <div className="progress" role="progressbar" aria-valuenow={percent} aria-valuemin={0} aria-valuemax={100}>
                <span style={{ width: `${percent}%` }} />
              </div>
              <div className="dim">
                {progress?.phase === 'checking'
                  ? 'Checking the file…'
                  : `${formatBytes(progress?.sent || 0)} of ${formatBytes(progress?.total || 0)} (${percent}%)${progress?.resumed ? ' · resumed' : ''}`}
              </div>
            </>
          ) : (
            <span className="dim">{data.entries.length + (data.own?.currentVersionId ? 1 : 0)} file(s)</span>
          )}
          {error && <p className="notice error">{error}</p>}
        </td>
        <td className="actions">
          <div className="button-row end">
            <input ref={inputRef} type="file" multiple hidden onChange={handlePick} />
            {queue ? (
              <button type="button" className="btn btn-sm" onClick={() => controllerRef.current?.abort()}>Cancel</button>
            ) : (
              <button type="button" className="btn btn-sm btn-primary" onClick={() => inputRef.current?.click()}>
                Add {PART_LABEL[part].toLowerCase()} files
              </button>
            )}
          </div>
        </td>
      </tr>
      {data.own?.currentVersionId && (
        <FileRow file={data.own} label="(uploaded before multiple files)" onChanged={onChanged} />
      )}
      {data.entries.map((entry) => (
        <FileRow key={entry.key} file={entry} label={entry.displayName} onChanged={onChanged} />
      ))}
    </>
  );
}

function FileGroup({ group, onChanged }) {
  const [error, setError] = useState('');
  const [editingFolder, setEditingFolder] = useState(false);
  const [folder, setFolder] = useState(group.modFolder || '');
  const presentParts = PARTS.filter((part) => group.parts[part]);
  const missing = PARTS.filter((part) => !group.parts[part]);

  async function save(parts, modFolder) {
    setError('');
    try {
      await saveDownloadFile(group.key, group.displayName, parts, modFolder);
      setEditingFolder(false);
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
          {presentParts.length > 0 && (
            editingFolder ? (
              <span className="inline-edit">
                <input value={folder} onChange={(e) => setFolder(e.target.value)} aria-label="Mod folder" />
                <button type="button" className="btn btn-sm btn-primary" onClick={() => save(presentParts, folder.trim())}>Save</button>
                <button type="button" className="btn btn-sm" onClick={() => setEditingFolder(false)}>Cancel</button>
              </span>
            ) : (
              <span className="dim">
                {' · '}folder {group.modFolder || 'not set'}{' '}
                <button type="button" className="btn-text" onClick={() => { setFolder(group.modFolder || ''); setEditingFolder(true); }}>
                  change
                </button>
              </span>
            )
          )}
          {group.single && presentParts.length === 0 && (
            <span className="dim"> · from before client and server parts; delete it once it is not needed</span>
          )}
          {error && <p className="notice error">{error}</p>}
        </td>
        <td className="actions">
          {presentParts.length > 0 && missing.length > 0 && (
            <div className="button-row end">
              {missing.map((part) => (
                <button key={part} type="button" className="btn btn-sm" onClick={() => save([part], group.modFolder)}>
                  + {PART_LABEL[part]} part
                </button>
              ))}
            </div>
          )}
        </td>
      </tr>
      {presentParts.map((part) => (
        <PartRows key={part} group={group} part={part} data={group.parts[part]} onChanged={onChanged} />
      ))}
      {group.single && (
        <FileRow file={group.single} label={<span className="status off">{PART_LABEL.single}</span>} showPlace={false} onChanged={onChanged} />
      )}
    </>
  );
}

function AddFileForm({ onAdded }) {
  const [key, setKey] = useState('');
  const [name, setName] = useState('');
  const [folder, setFolder] = useState('');
  const [parts, setParts] = useState(['client', 'server']);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const suggested = `@${name.replace(/[^A-Za-z0-9_.-]/g, '') || 'Mod'}`;

  const togglePart = (part) => setParts((list) => (list.includes(part) ? list.filter((p) => p !== part) : [...list, part]));

  async function handleSubmit(event) {
    event.preventDefault();
    setSaving(true);
    setError('');
    try {
      await saveDownloadFile(key.trim(), name.trim(), PARTS.filter((p) => parts.includes(p)), folder.trim() || suggested);
      setKey('');
      setName('');
      setFolder('');
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
        (what players load) and/or a server part (server-only PBOs); each part can hold several files.
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
        <label className="label" htmlFor="download-file-folder">Mod folder in the download</label>
        <input id="download-file-folder" className="input-block" value={folder} onChange={(e) => setFolder(e.target.value)}
          placeholder={suggested} />
        <span className="dim">.pbo and .bisign go in {folder.trim() || suggested}/addons, .bikey in /keys, the rest in the folder</span>
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
  const stripPart = (key) => key.match(KEY_PATTERN)?.[1] || key;
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

  const groups = groupFiles(overview?.files || [], overview?.modFolders);
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
