import { useCallback, useEffect, useRef, useState } from 'react';
import {
  deleteGameServer,
  getMapLinkPlayer,
  getMapLinkStateText,
  getPlatform,
  listGameServers,
  listMapLinkPlayers,
  releaseMapLinkPlayer,
  registerGameServer,
  restoreMapLinkState,
  setGameServerEnabled,
} from '../services/universalAdminService';

// The admin page's MapLink and Game servers sections. Both talk to this community's OverKill Universal API through
// the backend, which checks the admin and records who made each change.

const STATES = ['TRANSIT', 'ALIVE', 'DEAD', 'NONE'];
const STATE_LABEL = { ALIVE: 'Alive', TRANSIT: 'In transit', DEAD: 'Dead', NONE: 'Not seen' };
const STATE_CLASS = { ALIVE: 'ok', TRANSIT: '', DEAD: 'off', NONE: 'off' };
const ONLINE_SECONDS = 120; // servers send a heartbeat a minute

const MESSAGES = {
  admin_not_configured: 'The game service is not connected to this site yet. Set UNIVERSAL_API_BASE_URL and UNIVERSAL_API_ADMIN_KEY on the backend.',
  universal_api_unavailable: 'The game service is unreachable. Try again shortly.',
  universal_api_rejected_key: 'The game service refused this site\'s admin key. Check UNIVERSAL_API_ADMIN_KEY.',
  login_unavailable: 'Admin access could not be checked because the sign-in service is unreachable.',
  forbidden: 'Your account is not an admin.',
  unauthorized: 'Sign in again.',
  invalid_steam_id: 'A Steam64 ID is 17 digits and starts with 7656119.',
  invalid_state: 'Unknown state filter.',
  invalid_server_id: 'Server IDs use letters, numbers and - _ . ~ only (max 64).',
  invalid_api_key: 'API keys are 16-256 characters of letters, numbers and - _ . ~.',
  server_id_taken: 'A server with that ID is already registered.',
  server_not_found: 'That server no longer exists.',
  server_enabled: 'Disable the server before deleting it.',
  server_required: 'Pick a server to restore to.',
};

const describe = (error) => MESSAGES[error?.message] || error?.message || 'Something went wrong.';

function timeAgo(iso) {
  if (!iso) return '—';
  const seconds = Math.round((Date.now() - new Date(iso).getTime()) / 1000);
  if (Number.isNaN(seconds)) return '—';
  if (seconds < 60) return seconds <= 5 ? 'just now' : `${seconds} s ago`;
  if (seconds < 3600) return `${Math.round(seconds / 60)} min ago`;
  if (seconds < 86400) return `${Math.round(seconds / 3600)} h ago`;
  return `${Math.round(seconds / 86400)} d ago`;
}

const dateTime = (iso) => (iso ? new Date(iso).toLocaleString() : '—');
const route = (from, to) => (to ? `${from ?? '?'} → ${to}` : from ?? '—');
const formatBytes = (bytes) => (bytes < 1024 ? `${bytes} B` : `${(bytes / 1024).toFixed(1)} KB`);
const isOnline = (s) => !!s.heartbeatAt && (Date.now() - new Date(s.heartbeatAt).getTime()) / 1000 < ONLINE_SECONDS;

function saveText(filename, text) {
  const url = URL.createObjectURL(new Blob([text], { type: 'application/json' }));
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.append(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function StateBadge({ state }) {
  return <span className={`status ${STATE_CLASS[state] ?? 'off'}`}>{STATE_LABEL[state] ?? state}</span>;
}

function MapLinkAdmin({ servers }) {
  const [filter, setFilter] = useState('');
  const [rows, setRows] = useState(null);
  const [listError, setListError] = useState('');
  const [steamQuery, setSteamQuery] = useState('');
  const [player, setPlayer] = useState(null);
  const [viewing, setViewing] = useState(null);
  const [restoring, setRestoring] = useState(null);
  const [target, setTarget] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [confirmRelease, setConfirmRelease] = useState(false);
  const detailRef = useRef(null);

  const load = useCallback((state) => {
    listMapLinkPlayers(state)
      .then((list) => { setRows(Array.isArray(list) ? list : []); setListError(''); })
      .catch((err) => setListError(describe(err)));
  }, []);

  useEffect(() => { load(''); }, [load]);

  const enabledServers = servers.filter((s) => s.enabled);
  const serverName = (id) => {
    const server = servers.find((s) => s.serverId === id);
    return server?.displayName ? `${server.displayName} (${id})` : id ?? '—';
  };

  function changeFilter(next) {
    setFilter(next);
    setRows(null);
    load(next);
  }

  async function open(steamId) {
    setError('');
    setNotice('');
    setViewing(null);
    setRestoring(null);
    setConfirmRelease(false);
    try {
      setPlayer(await getMapLinkPlayer(steamId));
      requestAnimationFrame(() => detailRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' }));
    } catch (err) {
      setListError(describe(err));
    }
  }

  // Kills the character on record: the player spawns fresh on any server at their next login.
  async function release() {
    setBusy(true);
    setError('');
    try {
      await releaseMapLinkPlayer(player.steamId);
      setNotice('Character killed. The player spawns fresh on any server at their next login. Their saved characters below can still be restored.');
      setPlayer(await getMapLinkPlayer(player.steamId));
      load(filter);
    } catch (err) {
      setError(describe(err));
    } finally {
      setBusy(false);
      setConfirmRelease(false);
    }
  }

  function submitOpen(e) {
    e.preventDefault();
    const id = steamQuery.trim();
    if (!/^\d{17}$/.test(id)) {
      setListError(MESSAGES.invalid_steam_id);
      return;
    }
    setListError('');
    open(id);
  }

  const fileName = (c) => `maplink-${player.steamId}-character-${c.id}.json`;

  async function view(character) {
    setError('');
    try {
      setViewing({ character, raw: await getMapLinkStateText(player.steamId, character.id) });
    } catch (err) {
      setError(describe(err));
    }
  }

  async function download(character) {
    setError('');
    try {
      saveText(fileName(character), await getMapLinkStateText(player.steamId, character.id));
    } catch (err) {
      setError(describe(err));
    }
  }

  function startRestore(character) {
    setError('');
    setNotice('');
    setViewing(null);
    // Their current server when it can take it, else where the character was saved.
    const preferred = enabledServers.some((s) => s.serverId === player.serverId) ? player.serverId : character.serverId;
    setTarget(enabledServers.some((s) => s.serverId === preferred) ? preferred : enabledServers[0]?.serverId ?? '');
    setRestoring(character);
  }

  async function restore(e) {
    e.preventDefault();
    if (!restoring || !target) return;
    setBusy(true);
    setError('');
    try {
      const result = await restoreMapLinkState(player.steamId, restoring.id, target);
      setNotice(`Character #${result.restoredStateId} restored to ${serverName(result.serverId)}. An online player gets it within a few seconds; an offline one at their next login.`);
      setRestoring(null);
      setPlayer(await getMapLinkPlayer(player.steamId));
      load(filter);
    } catch (err) {
      setError(describe(err));
    } finally {
      setBusy(false);
    }
  }

  const pretty = (raw) => {
    try { return JSON.stringify(JSON.parse(raw).payload, null, 2); } catch { return raw; }
  };

  return (
    <section className="section">
      <div className="section-head">
        <h2>MapLink</h2>
      </div>
      <p className="muted">
        Where every player&apos;s character is right now: alive on a server, in transit between two (stored until they
        log in at the destination), or dead. Every time MapLink carries a character it keeps a copy, so if a trip went
        wrong you can give the player one back.
      </p>

      <div className="chips field">
        <button type="button" className={filter === '' ? 'chip active' : 'chip'} onClick={() => changeFilter('')}>All</button>
        {STATES.map((s) => (
          <button key={s} type="button" className={filter === s ? 'chip active' : 'chip'} onClick={() => changeFilter(s)}>
            {STATE_LABEL[s]}
          </button>
        ))}
        <button type="button" className="chip" onClick={() => changeFilter(filter)}>Refresh</button>
      </div>

      <form onSubmit={submitOpen} className="field">
        <label className="label" htmlFor="maplink-steam-id">Open a player</label>
        <div className="button-row">
          <input
            id="maplink-steam-id"
            className="input-block"
            type="text"
            inputMode="numeric"
            placeholder="7656119XXXXXXXXXX"
            value={steamQuery}
            onChange={(e) => setSteamQuery(e.target.value)}
            maxLength={17}
          />
          <button type="submit" className="btn btn-primary">Open</button>
        </div>
      </form>

      {listError && <p className="notice error">{listError}</p>}

      <div className="stack">
        <div>
          {rows === null && !listError && <p className="notice">Loading…</p>}
          {rows !== null && rows.length === 0 && (
            <p className="empty">
              {filter ? `Nobody is ${STATE_LABEL[filter].toLowerCase()} right now.` : 'No characters yet. Players appear once they join a server running MapLink.'}
            </p>
          )}
          {rows !== null && rows.length > 0 && (
            <div className="table-wrap">
              <table className="data-table compact">
                <thead>
                  <tr><th>Player</th><th>State</th><th>Where</th><th>Changed</th></tr>
                </thead>
                <tbody>
                  {rows.map((r) => (
                    <tr key={r.steamId}>
                      <td>
                        <button type="button" className="chip" onClick={() => open(r.steamId)}>
                          {r.playerName || 'Unknown name'}
                        </button>
                        <div className="code dim">{r.steamId}</div>
                      </td>
                      <td><StateBadge state={r.state} /></td>
                      <td className="code">{route(r.serverId, r.state === 'TRANSIT' ? r.targetServerId : null)}</td>
                      <td className="dim">{timeAgo(r.updatedAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          {rows !== null && rows.length >= 50 && (
            <p className="muted">Showing the 50 most recent. Open a player by Steam64 ID to see anyone else.</p>
          )}
        </div>

        <div ref={detailRef}>
          {!player && <p className="muted">Pick a player to see where their character is and what MapLink saved.</p>}
          {player && (
            <div className="panel">
              <h3>{rows?.find((r) => r.steamId === player.steamId)?.playerName || 'Unknown name'}</h3>
              <p className="code dim">{player.steamId}</p>
              <p>
                <StateBadge state={player.state} />{' '}
                {player.state === 'TRANSIT'
                  ? `${serverName(player.serverId)} → ${serverName(player.targetServerId)}`
                  : serverName(player.serverId)}
                {' '}· life {player.lifeId} · changed {timeAgo(player.updatedAt)}
              </p>

              {notice && <p className="notice ok">{notice}</p>}
              {error && <p className="notice error">{error}</p>}

              {(player.state === 'ALIVE' || player.state === 'TRANSIT') && (
                <div className="field">
                  {confirmRelease ? (
                    <>
                      <p className="notice warn">
                        This kills the character on record, so the player can spawn fresh on any map at their next
                        login. Saved characters stay and can still be restored. A player online right now keeps
                        playing until they log out.
                      </p>
                      <div className="button-row">
                        <button type="button" className="btn btn-danger" onClick={release} disabled={busy}>Confirm kill</button>
                        <button type="button" className="btn" onClick={() => setConfirmRelease(false)}>Cancel</button>
                      </div>
                    </>
                  ) : (
                    <button type="button" className="btn btn-danger" onClick={() => setConfirmRelease(true)}>
                      Kill character (spawn anywhere)
                    </button>
                  )}
                </div>
              )}

              <h3>Saved characters</h3>
              {player.states.length === 0 ? (
                <p className="muted">Nothing saved yet. A copy is saved every time MapLink carries this player to another server.</p>
              ) : (
                <div className="table-wrap">
                  <table className="data-table compact">
                    <thead>
                      <tr><th>#</th><th>Event</th><th>From → to</th><th>Life</th><th>Size</th><th>Saved</th><th /></tr>
                    </thead>
                    <tbody>
                      {player.states.map((c) => (
                        <tr key={c.id}>
                          <td className="code">{c.id}</td>
                          <td>{c.event === 'RESTORE' ? 'Admin restore' : c.event === 'DEPART' ? 'Trip' : c.event}</td>
                          <td className="code">{route(c.serverId, c.targetServerId)}</td>
                          <td>{c.lifeId}</td>
                          <td>{formatBytes(c.bytes)}</td>
                          <td className="dim" title={dateTime(c.createdAt)}>{timeAgo(c.createdAt)}</td>
                          <td className="actions">
                            <div className="button-row end">
                              <button type="button" className="btn btn-sm" onClick={() => view(c)}>View</button>
                              <button type="button" className="btn btn-sm" onClick={() => download(c)}>Download</button>
                              <button type="button" className="btn btn-sm btn-primary" onClick={() => startRestore(c)}>Restore</button>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}

              {restoring && (
                <form className="panel" onSubmit={restore}>
                  <h3>Restore character #{restoring.id}</h3>
                  {enabledServers.length === 0 ? (
                    <p className="notice error">No enabled game servers to restore to.</p>
                  ) : (
                    <div className="field">
                      <label className="label" htmlFor="restore-target">Give it to the player on</label>
                      <select id="restore-target" value={target} onChange={(e) => setTarget(e.target.value)} disabled={busy}>
                        {enabledServers.map((s) => (
                          <option key={s.serverId} value={s.serverId}>
                            {s.displayName ? `${s.displayName} (${s.serverId})` : s.serverId}
                          </option>
                        ))}
                      </select>
                    </div>
                  )}
                  <p className="notice warn">
                    This replaces the character the player has now. If they are online, within a few seconds their
                    current character and its gear are removed and they get this one: right where they stand when it is
                    their current server, otherwise they are sent to the chosen server. If they are offline, they get it
                    at their next login.
                  </p>
                  <div className="button-row">
                    <button type="submit" className="btn btn-danger" disabled={busy || !target}>Restore character</button>
                    <button type="button" className="btn" onClick={() => setRestoring(null)}>Cancel</button>
                  </div>
                </form>
              )}

              {viewing && (
                <div className="panel">
                  <h3>Character #{viewing.character.id}</h3>
                  <p className="muted">
                    {route(viewing.character.serverId, viewing.character.targetServerId)}, life {viewing.character.lifeId},
                    {' '}saved {dateTime(viewing.character.createdAt)}, {formatBytes(viewing.character.bytes)}
                  </p>
                  <pre className="code" style={{ maxHeight: 320, overflow: 'auto' }}>{pretty(viewing.raw)}</pre>
                  <div className="button-row">
                    <button type="button" className="btn" onClick={() => saveText(fileName(viewing.character), viewing.raw)}>Download JSON</button>
                    <button type="button" className="btn" onClick={() => setViewing(null)}>Close</button>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </section>
  );
}

function ServersAdmin({ servers, info, loadError, reload }) {
  const [rowError, setRowError] = useState('');
  const [confirmDisable, setConfirmDisable] = useState(null);
  const [confirmDelete, setConfirmDelete] = useState(null);
  const [newId, setNewId] = useState('');
  const [newKey, setNewKey] = useState('');
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState('');
  const [registered, setRegistered] = useState(null);
  const [copied, setCopied] = useState('');

  async function toggle(server, enabled) {
    setRowError('');
    try {
      await setGameServerEnabled(server.serverId, enabled);
      reload();
    } catch (err) {
      setRowError(describe(err));
    } finally {
      setConfirmDisable(null);
    }
  }

  async function remove(server) {
    setRowError('');
    try {
      await deleteGameServer(server.serverId);
      reload();
    } catch (err) {
      setRowError(describe(err));
    } finally {
      setConfirmDelete(null);
    }
  }

  async function register(e) {
    e.preventDefault();
    setBusy(true);
    setFormError('');
    setRegistered(null);
    try {
      setRegistered(await registerGameServer(newId.trim(), newKey.trim()));
      setNewId('');
      setNewKey('');
      reload();
    } catch (err) {
      setFormError(describe(err));
    } finally {
      setBusy(false);
    }
  }

  // The ApiUrl game servers use: set GAME_API_URL on the Universal API, else this site's own /api/dayz/.
  const gameApiUrl = info?.gameApiUrl || `${window.location.origin}/api/dayz/`;
  const snippet = registered
    ? JSON.stringify({
      ApiUrl: gameApiUrl.endsWith('/') ? gameApiUrl : `${gameApiUrl}/`,
      ServerId: registered.serverId,
      ApiKey: registered.apiKey,
      EnableDebug: 0,
      TimeoutSeconds: 10,
      RunTestOnConnect: 0,
    }, null, 4)
    : '';

  async function copy(label, text) {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(label);
    } catch {
      setCopied('');
    }
  }

  return (
    <section className="section">
      <div className="section-head">
        <h2>Game servers</h2>
        <div className="chips"><button type="button" className="chip" onClick={reload}>Refresh</button></div>
      </div>
      <p className="muted">
        Every DayZ server allowed to use this community&apos;s game service: MapLink travel, the character lock and
        skins. &quot;Online&quot; means a heartbeat in the last 2 minutes. Disabling a server locks it out at once.
        {info?.sharedServerKey && ' Servers using the shared server key are added here the first time they connect.'}
      </p>

      {loadError && <p className="notice error">{loadError}</p>}
      {rowError && <p className="notice error">{rowError}</p>}
      {servers === null && !loadError && <p className="notice">Loading…</p>}
      {servers !== null && servers.length === 0 && <p className="empty">No game servers registered yet.</p>}

      {servers !== null && servers.length > 0 && (
        <div className="table-wrap">
          <table className="data-table compact">
            <thead>
              <tr><th>Server</th><th>Address</th><th>Players</th><th>Terminals</th><th>Heartbeat</th><th>Status</th><th /></tr>
            </thead>
            <tbody>
              {servers.map((s) => (
                <tr key={s.serverId}>
                  <td>
                    {s.displayName ?? <em>Not published yet</em>}
                    <div className="code dim">{s.serverId}</div>
                  </td>
                  <td className="code">{s.ip ? `${s.ip}:${s.port}` : '—'}</td>
                  <td>{s.heartbeatAt ? `${s.playerCount}/${s.maxPlayers}` : '—'}</td>
                  <td>{s.displayName ? (s.terminals ?? []).length : '—'}</td>
                  <td title={dateTime(s.heartbeatAt)}>
                    {s.heartbeatAt
                      ? <><span className={isOnline(s) ? 'status ok' : 'status off'}>{isOnline(s) ? 'Online' : 'Stale'}</span> {timeAgo(s.heartbeatAt)}</>
                      : '—'}
                  </td>
                  <td><span className={s.enabled ? 'status ok' : 'status off'}>{s.enabled ? 'Enabled' : 'Disabled'}</span></td>
                  <td className="actions">
                    <div className="button-row end">
                      {!s.enabled ? (
                        <>
                          <button type="button" className="btn btn-sm" onClick={() => toggle(s, true)}>Enable</button>
                          {confirmDelete === s.serverId ? (
                            <>
                              <button type="button" className="btn btn-sm btn-danger" onClick={() => remove(s)}>Confirm delete</button>
                              <button type="button" className="btn btn-sm" onClick={() => setConfirmDelete(null)}>Keep</button>
                            </>
                          ) : (
                            <button type="button" className="btn btn-sm btn-danger" onClick={() => setConfirmDelete(s.serverId)}>Delete</button>
                          )}
                        </>
                      ) : confirmDisable === s.serverId ? (
                        <>
                          <button type="button" className="btn btn-sm btn-danger" onClick={() => toggle(s, false)}>Confirm disable</button>
                          <button type="button" className="btn btn-sm" onClick={() => setConfirmDisable(null)}>Keep</button>
                        </>
                      ) : (
                        <button type="button" className="btn btn-sm" onClick={() => setConfirmDisable(s.serverId)}>Disable</button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <form onSubmit={register} className="panel panel-accent">
        <h3>Register a server</h3>
        <p className="muted">
          {info?.sharedServerKey
            ? 'Optional: only for a server that should have its own key instead of the shared server key.'
            : 'Each DayZ server needs its own key. Register it here, then put the JSON shown in that server\'s profile at OverKill/API/OverKillAPI.json and restart it.'}
        </p>
        <div className="field">
          <label className="label" htmlFor="new-server-id">Server ID</label>
          <input
            id="new-server-id"
            className="input-block"
            value={newId}
            onChange={(e) => setNewId(e.target.value)}
            placeholder="overkill-main"
            pattern="[A-Za-z0-9._~\-]{1,64}"
            maxLength={64}
            spellCheck={false}
            required
          />
        </div>
        <div className="field">
          <label className="label" htmlFor="new-server-key">API key</label>
          <input
            id="new-server-key"
            className="input-block"
            value={newKey}
            onChange={(e) => setNewKey(e.target.value)}
            placeholder="Leave blank to generate one (16-256 characters)"
            pattern="[A-Za-z0-9._~\-]{16,256}"
            maxLength={256}
            spellCheck={false}
          />
        </div>
        {formError && <p className="notice error">{formError}</p>}
        <button type="submit" className="btn btn-primary" disabled={busy || !newId.trim()}>
          {busy ? 'Registering…' : 'Register server'}
        </button>
      </form>

      {registered && (
        <div className="panel">
          <h3>{registered.serverId} registered</h3>
          <p className="notice warn">
            Copy the key now: only its hash is kept, so it can never be shown again. Put this in the server&apos;s{' '}
            <span className="code">profiles/OverKill/API/OverKillAPI.json</span> and restart it.
          </p>
          <pre className="code">{snippet}</pre>
          <div className="button-row">
            <button type="button" className="btn" onClick={() => copy('json', snippet)}>{copied === 'json' ? 'Copied' : 'Copy JSON'}</button>
            <button type="button" className="btn" onClick={() => copy('key', registered.apiKey)}>{copied === 'key' ? 'Copied' : 'Copy key only'}</button>
            <button type="button" className="btn" onClick={() => setRegistered(null)}>Done</button>
          </div>
        </div>
      )}
    </section>
  );
}

export default function UniversalAdmin() {
  const [info, setInfo] = useState(null);
  const [problem, setProblem] = useState('');
  const [servers, setServers] = useState(null);
  const [loadError, setLoadError] = useState('');

  const loadServers = useCallback(() => {
    listGameServers()
      .then((list) => { setServers(Array.isArray(list) ? list : []); setLoadError(''); })
      .catch((err) => setLoadError(describe(err)));
  }, []);

  useEffect(() => {
    getPlatform()
      .then((data) => { setInfo(data); loadServers(); })
      .catch((err) => setProblem(describe(err)));
  }, [loadServers]);

  if (problem) {
    return (
      <section className="section">
        <div className="section-head"><h2>MapLink &amp; game servers</h2></div>
        <p className="notice warn">{problem}</p>
      </section>
    );
  }
  if (!info) return null;

  return (
    <>
      {info.features?.mapLink !== false && <MapLinkAdmin servers={servers ?? []} />}
      <ServersAdmin servers={servers} info={info} loadError={loadError} reload={loadServers} />
    </>
  );
}
