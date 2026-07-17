import { useState, useEffect } from 'react';
import { callJson } from '../services/apiClient';

export default function GoogleSheetsImportView({ token, onClose }) {
  const [status, setStatus] = useState('');
  const [savedTemplates, setSavedTemplates] = useState(() => {
    try { return JSON.parse(localStorage.getItem('google_templates') || '[]'); } catch (e) { return []; }
  });
  const [confirmDelete, setConfirmDelete] = useState(null); // { id, name }
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [showCopyInput, setShowCopyInput] = useState(false);
  const [copyName, setCopyName] = useState('My Loot Table');

  // CrateSettings upload state
  const [crateFile, setCrateFile]         = useState(null);
  const [selectedSheetId, setSelectedSheetId] = useState('');
  const [crateStatus, setCrateStatus]     = useState('');

  // Load server-side registered templates on mount
  useEffect(() => {
    (async () => {
      if (!token) return;
      try {
        const res = await callJson('/api/google/templates', { headers: { Authorization: `Bearer ${token}` } });
        if (Array.isArray(res) && res.length > 0) {
          const mapped = res.map((r) => ({ id: r.spreadsheetId, name: r.name }));
          const merged = [...mapped, ...savedTemplates.filter(s => !mapped.find(m => m.id === s.id))];
          setSavedTemplates(merged);
          localStorage.setItem('google_templates', JSON.stringify(merged));
        }
      } catch (_) {}
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  async function linkGoogle() {
    try {
      setStatus('Opening Google consent...');
      const res = await callJson('/api/google/login-url', { headers: { Authorization: `Bearer ${token}` } });
      if (res?.url) {
        window.open(res.url, '_blank', 'noopener');
        setStatus('Consent opened in new tab. Complete the flow and return here.');
      } else {
        setStatus('No login URL returned.');
      }
    } catch (err) {
      setStatus(String(err));
    }
  }

  async function copyTemplate(name) {
    if (!name || !name.trim()) { setStatus('Please enter a name.'); return; }
    setShowCopyInput(false);
    try {
      setStatus('Copying template...');
      const res = await callJson('/api/google/copy-template', {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: name.trim() }),
      });
      if (res?.spreadsheetId) {
        const entry = { id: res.spreadsheetId, name: res.name || name.trim() };
        const next = [entry, ...savedTemplates.filter((s) => s.id !== entry.id)];
        setSavedTemplates(next);
        localStorage.setItem('google_templates', JSON.stringify(next));
        setStatus(`"${entry.name}" copied and saved to your templates.`);
      } else {
        setStatus('Copy failed — make sure your Google account is linked.');
      }
    } catch (err) {
      setStatus(String(err));
    }
  }

  async function downloadTemplate(template) {
    try {
      setStatus(`Fetching "${template.name}"...`);
      // register-template re-syncs the name from Google and returns the parsed JSON
      const res = await callJson('/api/google/register-template', {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ spreadsheetId: template.id }),
      });
      if (!res?.parsed) { setStatus('Fetch failed.'); return; }

      // Sync name to whatever the Google Sheet is actually called now
      const latestName = res.name || template.name;
      const next = savedTemplates.map((s) => s.id === template.id ? { ...s, name: latestName } : s);
      setSavedTemplates(next);
      localStorage.setItem('google_templates', JSON.stringify(next));

      const blob = new Blob([JSON.stringify(res.parsed, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${latestName.replace(/[^a-z0-9_\- ]/gi, '_')}.json`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
      setStatus(`Downloaded "${latestName}".`);
    } catch (err) {
      setStatus(String(err));
    }
  }

  async function syncCrateSettings() {
    if (!crateFile || !selectedSheetId) {
      setCrateStatus('Select a JSON file and a target sheet first.');
      return;
    }
    setCrateStatus('Syncing…');
    try {
      const text = await crateFile.text();
      let crateSettings;
      try {
        crateSettings = JSON.parse(text);
      } catch (_) {
        setCrateStatus('Invalid JSON — could not parse CrateSettings file.');
        return;
      }
      const res = await callJson('/api/google/sync-crate-settings', {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ spreadsheetId: selectedSheetId, crateSettings }),
      });
      const updated = res?.updated ?? [];
      const failed  = res?.failed  ?? [];
      setCrateStatus(
        `Updated: ${updated.join(', ') || 'none'}${failed.length ? ` | Failed: ${failed.join(', ')}` : ''}`
      );
    } catch (err) {
      setCrateStatus('Sync failed: ' + String(err));
    }
  }

  async function executeDelete(id) {
    try {
      await callJson(`/api/google/templates?spreadsheetId=${encodeURIComponent(id)}`, {
        method: 'DELETE',
        headers: { Authorization: `Bearer ${token}` },
      });
    } catch (_) {}
    const next = savedTemplates.filter((s) => s.id !== id);
    setSavedTemplates(next);
    localStorage.setItem('google_templates', JSON.stringify(next));
    setConfirmDelete(null);
    setStatus('Template removed from your saved list.');
  }

  return (
    <div style={styles.backdrop} onClick={onClose}>
      <div style={styles.modal} onClick={(e) => e.stopPropagation()}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 14 }}>
          <h3 style={{ margin: 0 }}>Import Loot Table (Google Sheets)</h3>
          <div style={{ position: 'relative' }}>
            <button
              title="Settings"
              onClick={() => setSettingsOpen(v => !v)}
              style={{ background: 'none', border: 'none', cursor: 'pointer', color: settingsOpen ? '#7abaff' : '#aaa', padding: 4, lineHeight: 0 }}
            >
              <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
                <path d="M19.14 12.94c.04-.3.06-.61.06-.94s-.02-.64-.07-.94l2.03-1.58a.49.49 0 0 0 .12-.61l-1.92-3.32a.49.49 0 0 0-.59-.22l-2.39.96a7.01 7.01 0 0 0-1.62-.94l-.36-2.54A.484.484 0 0 0 14 2h-4a.484.484 0 0 0-.48.41l-.36 2.54a7.01 7.01 0 0 0-1.62.94l-2.39-.96a.477.477 0 0 0-.59.22L2.64 8.47a.47.47 0 0 0 .12.61l2.03 1.58c-.05.3-.07.62-.07.94s.02.64.07.94l-2.03 1.58a.47.47 0 0 0-.12.61l1.92 3.32c.12.22.37.3.59.22l2.39-.96c.5.36 1.04.67 1.62.94l.36 2.54c.05.24.27.41.48.41h4c.24 0 .44-.17.47-.41l.36-2.54a7.01 7.01 0 0 0 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32a.47.47 0 0 0-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z"/>
              </svg>
            </button>
            {settingsOpen && (
              <div style={{ position: 'absolute', right: 0, top: '100%', marginTop: 4, background: '#1a1a2e', border: '1px solid #444', borderRadius: 6, padding: 8, zIndex: 10, minWidth: 200, display: 'flex', flexDirection: 'column', gap: 6 }}>
                <button style={styles.btn} onClick={() => { setSettingsOpen(false); linkGoogle(); }}>Link Google Account</button>
                <button style={{ ...styles.btn, background: '#2a4a7a' }} onClick={() => { setSettingsOpen(false); setShowCopyInput(true); }}>Copy Template</button>
              </div>
            )}
          </div>
        </div>

        {/* Copy Template inline input */}
        {showCopyInput && (
          <div style={{ background: '#1a2030', border: '1px solid #445', borderRadius: 6, padding: 10, marginBottom: 10 }}>
            <label style={{ color: '#bbb', fontSize: 12, display: 'block', marginBottom: 6 }}>Name your template copy:</label>
            <div style={{ display: 'flex', gap: 8 }}>
              <input
                autoFocus
                type="text"
                value={copyName}
                onChange={(e) => setCopyName(e.target.value)}
                onKeyDown={(e) => { if (e.key === 'Enter') copyTemplate(copyName); if (e.key === 'Escape') setShowCopyInput(false); }}
                style={{ flex: 1, padding: '4px 8px', borderRadius: 4, border: '1px solid #555', background: '#111', color: '#eee', fontSize: 13 }}
              />
              <button style={{ ...styles.btn, background: '#2a4a7a' }} onClick={() => copyTemplate(copyName)}>Copy</button>
              <button style={styles.btn} onClick={() => setShowCopyInput(false)}>Cancel</button>
            </div>
          </div>
        )}

        {/* Delete confirmation dialog */}
        {confirmDelete && (
          <div style={styles.confirmBox}>
            <p style={{ margin: '0 0 10px', lineHeight: 1.5 }}>
              Remove <strong>{confirmDelete.name}</strong> from your saved templates?
              <br />
              <span style={{ fontSize: 12, color: '#999' }}>
                This only removes it from this website — your actual Google Sheet will not be deleted.
              </span>
            </p>
            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
              <button style={styles.btn} onClick={() => setConfirmDelete(null)}>Cancel</button>
              <button style={{ ...styles.btn, background: '#7a1010' }} onClick={() => executeDelete(confirmDelete.id)}>
                Remove
              </button>
            </div>
          </div>
        )}

        {/* Saved templates list */}
        {savedTemplates.length > 0 && (
          <section style={{ marginBottom: 10 }}>
            <label style={{ color: '#bbb', fontSize: 12, display: 'block', marginBottom: 6 }}>Saved Templates</label>
            <ul style={{ margin: 0, padding: 0, listStyle: 'none', maxHeight: 200, overflow: 'auto' }}>
              {savedTemplates.map((s) => (
                <li key={s.id} style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 6 }}>
                  <a
                    href={`https://docs.google.com/spreadsheets/d/${s.id}`}
                    target="_blank"
                    rel="noopener noreferrer"
                    style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', color: '#7abaff', textDecoration: 'none' }}
                    title="Open in Google Sheets"
                  >{s.name}</a>
                  <button style={styles.btn} onClick={() => downloadTemplate(s)}>Download</button>
                  <button style={{ ...styles.btn, background: '#600' }} onClick={() => setConfirmDelete(s)}>Delete</button>
                </li>
              ))}
            </ul>
          </section>
        )}

        {status && <div style={{ fontSize: 12, color: '#aaa', marginBottom: 8 }}>{status}</div>}

        {/* CrateSettings → Google Sheet sync */}
        <section style={{ borderTop: '1px solid #333', paddingTop: 10, marginTop: 10 }}>
          <label style={{ color: '#bbb', fontSize: 12, display: 'block', marginBottom: 6 }}>
            Upload CrateSettings (sync LootMaster → Google Sheet)
          </label>
          <input
            type="file"
            accept=".json"
            onChange={(e) => { setCrateFile(e.target.files?.[0] ?? null); setCrateStatus(''); }}
            style={{ fontSize: 12, marginBottom: 6, width: '100%', color: '#eee' }}
          />
          {savedTemplates.length > 0 && (
            <select
              value={selectedSheetId}
              onChange={(e) => setSelectedSheetId(e.target.value)}
              style={{
                background: '#2a2a3e', color: '#eee', border: '1px solid #444',
                borderRadius: 4, padding: '3px 6px', fontSize: 12,
                width: '100%', marginBottom: 6,
              }}
            >
              <option value="" disabled>Select target sheet…</option>
              {savedTemplates.map((s) => (
                <option key={s.id} value={s.id}>{s.name}</option>
              ))}
            </select>
          )}
          <button
            style={{ ...styles.btn, background: '#1a5e3a', width: '100%' }}
            onClick={syncCrateSettings}
            disabled={!crateFile || !selectedSheetId}
          >
            Sync LootMaster to Sheet
          </button>
          {crateStatus && <div style={{ fontSize: 12, color: '#aaa', marginTop: 6 }}>{crateStatus}</div>}
        </section>

        <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 12 }}>
          <button style={styles.btn} onClick={onClose}>Close</button>
        </div>
      </div>
    </div>
  );
}

const styles = {
  backdrop: {
    position: 'absolute', inset: 0, background: 'rgba(0,0,0,0.6)',
    display: 'flex', alignItems: 'center', justifyContent: 'center',
  },
  modal: {
    width: 500, background: '#111', color: '#eee',
    border: '1px solid #333', borderRadius: 6, padding: 16,
  },
  btn: {
    background: '#3a3a5e', color: '#eee', border: 'none',
    borderRadius: 4, padding: '6px 12px', cursor: 'pointer', whiteSpace: 'nowrap',
  },
  input: {
    background: '#222', color: '#eee', border: '1px solid #444',
    borderRadius: 4, padding: '7px 10px', fontSize: 14,
  },
  confirmBox: {
    background: '#1a1a2e', border: '1px solid #555',
    borderRadius: 4, padding: 12, marginBottom: 12,
  },
};
