import { useState, useEffect } from 'react';
import { callJson } from '../services/apiClient';

export default function GoogleSheetsImportView({ token, onClose }) {
  const [status, setStatus] = useState('');
  const [savedTemplates, setSavedTemplates] = useState(() => {
    try { return JSON.parse(localStorage.getItem('google_templates') || '[]'); } catch (e) { return []; }
  });
  const [confirmDelete, setConfirmDelete] = useState(null); // { id, name }

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

  async function copyTemplate() {
    const name = window.prompt('Name your template copy:', 'My Loot Table');
    if (name === null) return; // user cancelled
    if (!name.trim()) { setStatus('Please enter a name.'); return; }
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
        <h3 style={{ marginTop: 0, marginBottom: 14 }}>Import Loot Table (Google Sheets)</h3>

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

        {/* Top action bar */}
        <div style={{ display: 'flex', gap: 8, marginBottom: 10 }}>
          <button style={styles.btn} onClick={linkGoogle}>Link Google Account</button>
          <button style={{ ...styles.btn, background: '#2a4a7a' }} onClick={copyTemplate}>Copy Template</button>
        </div>

        {/* Saved templates list */}
        {savedTemplates.length > 0 && (
          <section style={{ marginBottom: 10 }}>
            <label style={{ color: '#bbb', fontSize: 12, display: 'block', marginBottom: 6 }}>Saved Templates</label>
            <ul style={{ margin: 0, padding: 0, listStyle: 'none', maxHeight: 200, overflow: 'auto' }}>
              {savedTemplates.map((s) => (
                <li key={s.id} style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 6 }}>
                  <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{s.name}</span>
                  <button style={styles.btn} onClick={() => downloadTemplate(s)}>Download</button>
                  <button style={{ ...styles.btn, background: '#600' }} onClick={() => setConfirmDelete(s)}>Delete</button>
                </li>
              ))}
            </ul>
          </section>
        )}

        {status && <div style={{ fontSize: 12, color: '#aaa', marginBottom: 8 }}>{status}</div>}

        <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
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
