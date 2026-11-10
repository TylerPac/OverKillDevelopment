import { useEffect, useState, useCallback } from 'react';
import DownloadsAdmin from './DownloadsAdmin';
import { listAccessCodes, createFullUnlockCode, createSpecificCode, revokeCode, deleteCode } from '../services/adminService';
import { markProductRelease } from '../services/apiClient';

const AVAILABLE_PRODUCTS = ['keycard-crates', 'weapon-system', 'battle-pass'];

function StatusBadge({ code }) {
  if (code.revoked) {
    return <span className="status off">Revoked</span>;
  }
  if (code.redeemed) {
    return <span className="status ok">Redeemed</span>;
  }
  return <span className="status live">Available</span>;
}

function CreateCodeForm({ onCreated }) {
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
        await createFullUnlockCode(customCode.trim() || null);
      } else {
        if (selectedProducts.length === 0) {
          setError('Select at least one product.');
          setLoading(false);
          return;
        }
        await createSpecificCode(customCode.trim() || null, selectedProducts);
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
    <form onSubmit={handleSubmit} className="panel panel-accent">
      <h3>Create Access Code</h3>

      <div className="chips field">
        <button
          type="button"
          onClick={() => setMode('full')}
          className={mode === 'full' ? 'chip active' : 'chip'}
        >
          Full Unlock
        </button>
        <button
          type="button"
          onClick={() => setMode('specific')}
          className={mode === 'specific' ? 'chip active' : 'chip'}
        >
          Specific Products
        </button>
      </div>

      {mode === 'specific' && (
        <div className="field">
          <span className="label">Products</span>
          <div className="chips">
            {AVAILABLE_PRODUCTS.map((p) => (
              <label key={p} className={selectedProducts.includes(p) ? 'chip active' : 'chip'}>
                <input
                  type="checkbox"
                  checked={selectedProducts.includes(p)}
                  onChange={() => toggleProduct(p)}
                />
                {p}
              </label>
            ))}
          </div>
        </div>
      )}

      <div className="field">
        <label className="label" htmlFor="admin-custom-code">
          Custom code <span className="dim">(optional — leave blank to auto-generate)</span>
        </label>
        <input
          id="admin-custom-code"
          className="input-block"
          type="text"
          placeholder="e.g. LAUNCH2024"
          value={customCode}
          onChange={(e) => setCustomCode(e.target.value)}
          maxLength={64}
        />
      </div>

      {error && <p className="notice error">{error}</p>}

      <button type="submit" className="btn btn-primary" disabled={loading}>
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
      <td className="code">{code.code}</td>
      <td>
        {code.fullAccess
          ? <span className="status ok">Full Unlock</span>
          : <span className="muted">{code.productIds?.join(', ') || '—'}</span>}
      </td>
      <td>
        <StatusBadge code={code} />
      </td>
      <td className="muted">{code.redeemedByUsername || '—'}</td>
      <td className="dim">{code.createdAt ? new Date(code.createdAt).toLocaleDateString() : '—'}</td>
      <td className="actions">
        {!code.revoked && (
          <div className="button-row end">
            <button
              type="button"
              onClick={handleRevoke}
              className={confirming ? 'btn btn-sm btn-danger' : 'btn btn-sm'}
              disabled={loading}
            >
              {loading && confirming ? '…' : confirming ? 'Confirm Revoke' : 'Revoke'}
            </button>
            {confirming && !loading && (
              <button type="button" onClick={() => setConfirming(false)} className="btn btn-sm">
                Cancel
              </button>
            )}
          </div>
        )}
        {code.revoked && (
          <div className="button-row end">
            <button
              type="button"
              onClick={handleDelete}
              className={confirmingDelete ? 'btn btn-sm btn-danger' : 'btn btn-sm'}
              disabled={loading}
            >
              {loading && confirmingDelete ? '…' : confirmingDelete ? 'Confirm Delete' : 'Delete'}
            </button>
            {confirmingDelete && !loading && (
              <button type="button" onClick={() => setConfirmingDelete(false)} className="btn btn-sm">
                Cancel
              </button>
            )}
          </div>
        )}
      </td>
    </tr>
  );
}

function MarkProductReleaseForm() {
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
      const data = await markProductRelease(selectedProduct);
      setResult(`Marked "${selectedProduct}" as updated at ${new Date(data.releasedAt).toLocaleString()}`);
    } catch (err) {
      setError(err.message || 'Failed to mark release.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="panel">
      <h3>Mark New File Release</h3>
      <p className="muted">
        When you upload a new version of a mod file, mark it here. Customers who
        haven&apos;t downloaded since this point will see a &ldquo;New Update&rdquo; badge.
      </p>
      <div className="chips field">
        {AVAILABLE_PRODUCTS.map((p) => (
          <label key={p} className={selectedProduct === p ? 'chip active' : 'chip'}>
            <input
              type="radio"
              name="releaseProduct"
              value={p}
              checked={selectedProduct === p}
              onChange={() => setSelectedProduct(p)}
            />
            {p}
          </label>
        ))}
      </div>
      {error && <p className="notice error">{error}</p>}
      {result && <p className="notice ok">{result}</p>}
      <button type="submit" className="btn btn-primary" disabled={loading}>
        {loading ? 'Saving…' : 'Mark as New Release'}
      </button>
    </form>
  );
}

export default function AdminView() {
  const [codes, setCodes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [filter, setFilter] = useState('all');

  const fetchCodes = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await listAccessCodes();
      setCodes(data);
    } catch (err) {
      setError(err.message || 'Failed to load codes.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchCodes(); }, [fetchCodes]);

  async function handleRevoke(id) {
    try {
      await revokeCode(id);
      await fetchCodes();
    } catch (err) {
      setError(err.message || 'Failed to revoke code.');
    }
  }

  async function handleDelete(id) {
    try {
      await deleteCode(id);
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
    <section>
      <header className="page-head">
        <p className="kicker"><span className="tag">Command</span> Admin</p>
        <h1>Admin — Access Codes</h1>
      </header>

      <div className="split">
        <CreateCodeForm onCreated={fetchCodes} />
        <MarkProductReleaseForm />
      </div>

      <section className="section">
        <div className="section-head">
          <h2>All Codes{!loading && ` (${codes.length})`}</h2>
          <div className="chips">
            {['all', 'available', 'redeemed', 'revoked'].map((f) => (
              <button
                key={f}
                type="button"
                onClick={() => setFilter(f)}
                className={filter === f ? 'chip active' : 'chip'}
              >
                {f}
              </button>
            ))}
            <button type="button" onClick={fetchCodes} className="chip">
              Refresh
            </button>
          </div>
        </div>

        {error && <p className="notice error">{error}</p>}

        {loading ? (
          <p className="notice">Loading…</p>
        ) : filtered.length === 0 ? (
          <p className="empty">No codes found.</p>
        ) : (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  {['Code', 'Scope', 'Status', 'Redeemed By', 'Created', ''].map((h) => (
                    <th key={h} className={h === '' ? 'actions' : undefined}>
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
      </section>

      <DownloadsAdmin />
    </section>
  );
}
