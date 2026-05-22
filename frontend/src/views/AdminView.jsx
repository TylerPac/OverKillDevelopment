import { useEffect, useState, useCallback } from 'react';
import { listAccessCodes, createFullUnlockCode, createSpecificCode, revokeCode, deleteCode } from '../services/adminService';
import { markProductRelease } from '../services/apiClient';

const AVAILABLE_PRODUCTS = ['keycard-crates', 'weapon-system', 'battle-pass'];

const cardStyle = {
  background: '#1a1d2e',
  border: '1px solid #2a2d3e',
  borderRadius: 8,
  padding: '1.25rem 1.5rem',
  marginBottom: '1.5rem',
};

const inputStyle = {
  background: '#12141f',
  border: '1px solid #2a2d3e',
  borderRadius: 4,
  color: '#e0e8ff',
  fontSize: '0.9rem',
  padding: '0.45rem 0.75rem',
  width: '100%',
  boxSizing: 'border-box',
};

const btnStyle = (variant = 'primary') => ({
  background: variant === 'primary' ? '#3a5cd8' : variant === 'danger' ? '#8b1a1a' : '#2a2d3e',
  border: 'none',
  borderRadius: 4,
  color: variant === 'muted' ? '#aab' : '#e0e8ff',
  cursor: 'pointer',
  fontSize: '0.85rem',
  padding: '0.4rem 1rem',
  whiteSpace: 'nowrap',
});

function StatusBadge({ code }) {
  if (code.revoked) {
    return <span style={{ color: '#e05', fontSize: '0.78rem', fontWeight: 600 }}>Revoked</span>;
  }
  if (code.redeemed) {
    return <span style={{ color: '#7e7', fontSize: '0.78rem', fontWeight: 600 }}>Redeemed</span>;
  }
  return <span style={{ color: '#5bf', fontSize: '0.78rem', fontWeight: 600 }}>Available</span>;
}

function CreateCodeForm({ token, onCreated }) {
  const [mode, setMode] = useState('full');
  const [customCode, setCustomCode] = useState('');
  const [selectedProducts, setSelectedProducts] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  function toggleProduct(id) {
    setSelectedProducts((prev) =>
      prev.includes(id) ? prev.filter((p) => p !== id) : [...prev, id]
    );
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      if (mode === 'full') {
        await createFullUnlockCode(token, customCode.trim() || null);
      } else {
        if (selectedProducts.length === 0) {
          setError('Select at least one product.');
          setLoading(false);
          return;
        }
        await createSpecificCode(token, customCode.trim() || null, selectedProducts);
      }
      setCustomCode('');
      setSelectedProducts([]);
      onCreated();
    } catch (err) {
      setError(err.message || 'Failed to create code.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} style={cardStyle}>
      <h3 style={{ margin: '0 0 1rem', color: '#c0cfff', fontSize: '1rem' }}>Create Access Code</h3>

      <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1rem' }}>
        <button
          type="button"
          onClick={() => setMode('full')}
          style={{ ...btnStyle(mode === 'full' ? 'primary' : 'muted'), borderRadius: 4 }}
        >
          Full Unlock
        </button>
        <button
          type="button"
          onClick={() => setMode('specific')}
          style={{ ...btnStyle(mode === 'specific' ? 'primary' : 'muted'), borderRadius: 4 }}
        >
          Specific Products
        </button>
      </div>

      {mode === 'specific' && (
        <div style={{ marginBottom: '1rem' }}>
          <div style={{ fontSize: '0.82rem', color: '#778', marginBottom: '0.4rem' }}>Products</div>
          <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
            {AVAILABLE_PRODUCTS.map((p) => (
              <label
                key={p}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.3rem',
                  cursor: 'pointer',
                  fontSize: '0.85rem',
                  color: selectedProducts.includes(p) ? '#8cf' : '#889',
                  background: selectedProducts.includes(p) ? '#1c2a4a' : '#181a27',
                  border: `1px solid ${selectedProducts.includes(p) ? '#3a5cd8' : '#2a2d3e'}`,
                  borderRadius: 4,
                  padding: '0.3rem 0.7rem',
                }}
              >
                <input
                  type="checkbox"
                  checked={selectedProducts.includes(p)}
                  onChange={() => toggleProduct(p)}
                  style={{ display: 'none' }}
                />
                {p}
              </label>
            ))}
          </div>
        </div>
      )}

      <div style={{ marginBottom: '1rem' }}>
        <div style={{ fontSize: '0.82rem', color: '#778', marginBottom: '0.4rem' }}>
          Custom code <span style={{ color: '#556' }}>(optional — leave blank to auto-generate)</span>
        </div>
        <input
          style={inputStyle}
          type="text"
          placeholder="e.g. LAUNCH2024"
          value={customCode}
          onChange={(e) => setCustomCode(e.target.value)}
          maxLength={64}
        />
      </div>

      {error && <div style={{ color: '#f66', fontSize: '0.82rem', marginBottom: '0.75rem' }}>{error}</div>}

      <button type="submit" style={btnStyle('primary')} disabled={loading}>
        {loading ? 'Creating…' : 'Create Code'}
      </button>
    </form>
  );
}

function CodeRow({ code, onRevoke, onDelete }) {
  const [confirming, setConfirming] = useState(false);
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const [loading, setLoading] = useState(false);

  async function handleRevoke() {
    if (!confirming) { setConfirming(true); return; }
    setLoading(true);
    await onRevoke(code.id);
    setLoading(false);
    setConfirming(false);
  }

  async function handleDelete() {
    if (!confirmingDelete) { setConfirmingDelete(true); return; }
    setLoading(true);
    await onDelete(code.id);
    setLoading(false);
    setConfirmingDelete(false);
  }

  return (
    <tr>
      <td style={{ padding: '0.55rem 0.75rem', fontFamily: 'monospace', fontSize: '0.9rem', color: '#c8d8ff' }}>
        {code.code}
      </td>
      <td style={{ padding: '0.55rem 0.75rem', fontSize: '0.82rem', color: '#889' }}>
        {code.fullAccess
          ? <span style={{ color: '#ad8' }}>Full Unlock</span>
          : (code.productIds?.join(', ') || '—')}
      </td>
      <td style={{ padding: '0.55rem 0.75rem' }}>
        <StatusBadge code={code} />
      </td>
      <td style={{ padding: '0.55rem 0.75rem', fontSize: '0.78rem', color: '#667' }}>
        {code.redeemedByUsername || '—'}
      </td>
      <td style={{ padding: '0.55rem 0.75rem', fontSize: '0.78rem', color: '#556' }}>
        {code.createdAt ? new Date(code.createdAt).toLocaleDateString() : '—'}
      </td>
      <td style={{ padding: '0.55rem 0.75rem', textAlign: 'right' }}>
        {!code.revoked && (
          <>
            <button
              type="button"
              onClick={handleRevoke}
              style={btnStyle(confirming ? 'danger' : 'muted')}
              disabled={loading}
            >
              {loading && confirming ? '…' : confirming ? 'Confirm Revoke' : 'Revoke'}
            </button>
            {confirming && !loading && (
              <button
                type="button"
                onClick={() => setConfirming(false)}
                style={{ ...btnStyle('muted'), marginLeft: '0.4rem' }}
              >
                Cancel
              </button>
            )}
          </>
        )}
        {code.revoked && (
          <>
            <button
              type="button"
              onClick={handleDelete}
              style={btnStyle(confirmingDelete ? 'danger' : 'muted')}
              disabled={loading}
            >
              {loading && confirmingDelete ? '…' : confirmingDelete ? 'Confirm Delete' : 'Delete'}
            </button>
            {confirmingDelete && !loading && (
              <button
                type="button"
                onClick={() => setConfirmingDelete(false)}
                style={{ ...btnStyle('muted'), marginLeft: '0.4rem' }}
              >
                Cancel
              </button>
            )}
          </>
        )}
      </td>
    </tr>
  );
}

function MarkProductReleaseForm({ token }) {
  const [selectedProduct, setSelectedProduct] = useState(AVAILABLE_PRODUCTS[0]);
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');

  async function handleSubmit(e) {
    e.preventDefault();
    setError('');
    setResult(null);
    setLoading(true);
    try {
      const data = await markProductRelease(selectedProduct, token);
      setResult(`Marked "${selectedProduct}" as updated at ${new Date(data.releasedAt).toLocaleString()}`);
    } catch (err) {
      setError(err.message || 'Failed to mark release.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} style={cardStyle}>
      <h3 style={{ margin: '0 0 1rem', color: '#c0cfff', fontSize: '1rem' }}>Mark New File Release</h3>
      <p style={{ color: '#778', fontSize: '0.82rem', marginBottom: '1rem', marginTop: 0 }}>
        When you upload a new version of a mod file, mark it here. Customers who
        haven&apos;t downloaded since this point will see a &ldquo;New Update&rdquo; badge.
      </p>
      <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap', marginBottom: '1rem' }}>
        {AVAILABLE_PRODUCTS.map((p) => (
          <label
            key={p}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '0.3rem',
              cursor: 'pointer',
              fontSize: '0.85rem',
              color: selectedProduct === p ? '#8cf' : '#889',
              background: selectedProduct === p ? '#1c2a4a' : '#181a27',
              border: `1px solid ${selectedProduct === p ? '#3a5cd8' : '#2a2d3e'}`,
              borderRadius: 4,
              padding: '0.3rem 0.7rem',
            }}
          >
            <input
              type="radio"
              name="releaseProduct"
              value={p}
              checked={selectedProduct === p}
              onChange={() => setSelectedProduct(p)}
              style={{ display: 'none' }}
            />
            {p}
          </label>
        ))}
      </div>
      {error && <div style={{ color: '#f66', fontSize: '0.82rem', marginBottom: '0.75rem' }}>{error}</div>}
      {result && <div style={{ color: '#4dde8a', fontSize: '0.82rem', marginBottom: '0.75rem' }}>{result}</div>}
      <button type="submit" style={btnStyle('primary')} disabled={loading}>
        {loading ? 'Saving…' : 'Mark as New Release'}
      </button>
    </form>
  );
}

export default function AdminView({ token }) {
  const [codes, setCodes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [filter, setFilter] = useState('all');

  const fetchCodes = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await listAccessCodes(token);
      setCodes(data);
    } catch (err) {
      setError(err.message || 'Failed to load codes.');
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => { fetchCodes(); }, [fetchCodes]);

  async function handleRevoke(id) {
    try {
      await revokeCode(token, id);
      await fetchCodes();
    } catch (err) {
      setError(err.message || 'Failed to revoke code.');
    }
  }

  async function handleDelete(id) {
    try {
      await deleteCode(token, id);
      await fetchCodes();
    } catch (err) {
      setError(err.message || 'Failed to delete code.');
    }
  }

  const filtered = codes.filter((c) => {
    if (filter === 'available') return !c.revoked && !c.redeemed;
    if (filter === 'redeemed') return c.redeemed && !c.revoked;
    if (filter === 'revoked') return c.revoked;
    return true;
  });

  return (
    <div style={{ maxWidth: 900, margin: '0 auto', padding: '2rem 1rem' }}>
      <h2 style={{ margin: '0 0 1.5rem', color: '#c0cfff', fontSize: '1.3rem', fontWeight: 700 }}>
        Admin — Access Codes
      </h2>

      <CreateCodeForm token={token} onCreated={fetchCodes} />

      <MarkProductReleaseForm token={token} />

      <div style={cardStyle}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1rem', flexWrap: 'wrap', gap: '0.5rem' }}>
          <h3 style={{ margin: 0, color: '#c0cfff', fontSize: '1rem' }}>
            All Codes{!loading && ` (${codes.length})`}
          </h3>
          <div style={{ display: 'flex', gap: '0.4rem' }}>
            {['all', 'available', 'redeemed', 'revoked'].map((f) => (
              <button
                key={f}
                type="button"
                onClick={() => setFilter(f)}
                style={{ ...btnStyle(filter === f ? 'primary' : 'muted'), textTransform: 'capitalize', fontSize: '0.8rem', padding: '0.3rem 0.7rem' }}
              >
                {f}
              </button>
            ))}
            <button type="button" onClick={fetchCodes} style={{ ...btnStyle('muted'), fontSize: '0.8rem', padding: '0.3rem 0.7rem' }}>
              Refresh
            </button>
          </div>
        </div>

        {error && <div style={{ color: '#f66', fontSize: '0.82rem', marginBottom: '0.75rem' }}>{error}</div>}

        {loading ? (
          <div style={{ color: '#556', fontSize: '0.9rem' }}>Loading…</div>
        ) : filtered.length === 0 ? (
          <div style={{ color: '#445', fontSize: '0.9rem' }}>No codes found.</div>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.88rem' }}>
              <thead>
                <tr style={{ borderBottom: '1px solid #2a2d3e' }}>
                  {['Code', 'Scope', 'Status', 'Redeemed By', 'Created', ''].map((h) => (
                    <th key={h} style={{ padding: '0.4rem 0.75rem', textAlign: h === '' ? 'right' : 'left', color: '#556', fontWeight: 600, fontSize: '0.78rem', whiteSpace: 'nowrap' }}>
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {filtered.map((code) => (
                  <CodeRow key={code.id} code={code} onRevoke={handleRevoke} onDelete={handleDelete} />
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
