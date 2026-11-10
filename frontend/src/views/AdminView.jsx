import { useEffect, useState } from 'react';
import DownloadsAdmin from './DownloadsAdmin';
import { listGrants, grantProducts, revokeGrant } from '../services/adminService';
import { getProducts } from '../services/shopService';
import { markProductRelease } from '../services/apiClient';

const STEAM_ID = /^7656119\d{10}$/;

const ERRORS = {
  invalid_steam_id: 'That is not a Steam64 ID. It is 17 digits and starts with 7656119.',
  invalid_product: 'One of those products no longer exists. Refresh the page.',
  product_ids_required: 'Pick at least one product.',
  grant_not_found: 'That grant no longer exists.',
  forbidden: 'You are not an admin.',
};

function errorText(err, fallback) {
  return ERRORS[err?.message] || err?.message || fallback;
}

function GrantForm({ products, onGranted }) {
  const [steamId, setSteamId] = useState('');
  const [selected, setSelected] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [result, setResult] = useState('');

  const names = Object.fromEntries(products.map((p) => [p.id, p.name]));

  function toggle(id) {
    setSelected((prev) => (prev.includes(id) ? prev.filter((p) => p !== id) : [...prev, id]));
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setError('');
    setResult('');
    const id = steamId.trim();
    if (!STEAM_ID.test(id)) {
      setError(ERRORS.invalid_steam_id);
      return;
    }
    if (selected.length === 0) {
      setError(ERRORS.product_ids_required);
      return;
    }
    setLoading(true);
    try {
      const data = await grantProducts(id, selected);
      const given = (data.granted || []).map((p) => names[p] || p);
      const owned = (data.alreadyOwned || []).map((p) => names[p] || p);
      setResult([
        given.length ? `Gave ${given.join(', ')} to ${id}.` : '',
        owned.length ? `Already owned: ${owned.join(', ')}.` : '',
      ].filter(Boolean).join(' '));
      setSelected([]);
      onGranted();
    } catch (err) {
      setError(errorText(err, 'Failed to give products.'));
    } finally {
      setLoading(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="panel panel-accent">
      <h3>Give Products</h3>
      <p className="muted">
        Unlocks products for a player for free. It works even if they have never signed in; the products are
        waiting when they do. Discounts are made in the Stripe dashboard as promotion codes.
      </p>

      <div className="field">
        <label className="label" htmlFor="grant-steam-id">Steam64 ID</label>
        <input
          id="grant-steam-id"
          className="input-block"
          type="text"
          inputMode="numeric"
          placeholder="7656119XXXXXXXXXX"
          value={steamId}
          onChange={(e) => setSteamId(e.target.value)}
          maxLength={17}
        />
      </div>

      <div className="field">
        <span className="label">Products</span>
        <div className="chips">
          {products.map((p) => (
            <label key={p.id} className={selected.includes(p.id) ? 'chip active' : 'chip'}>
              <input type="checkbox" checked={selected.includes(p.id)} onChange={() => toggle(p.id)} />
              {p.name}
            </label>
          ))}
        </div>
      </div>

      {error && <p className="notice error">{error}</p>}
      {result && <p className="notice ok">{result}</p>}

      <button type="submit" className="btn btn-primary" disabled={loading}>
        {loading ? 'Giving…' : 'Give Products'}
      </button>
    </form>
  );
}

function GrantRow({ grant, onRevoke }) {
  const [confirming, setConfirming] = useState(false);
  const [loading, setLoading] = useState(false);
  const revoked = grant.status === 'REVOKED';

  async function handleRevoke() {
    if (!confirming) { setConfirming(true); return; }
    setLoading(true);
    await onRevoke(grant.id);
    setLoading(false);
    setConfirming(false);
  }

  return (
    <tr>
      <td className="code">{grant.steamId || '—'}</td>
      <td>{grant.productName}</td>
      <td>
        {revoked
          ? <span className="status off">Revoked</span>
          : <span className="status ok">Active</span>}
      </td>
      <td className="muted">
        {grant.source === 'code' ? 'Old access code' : grant.grantedBy ? `Admin ${grant.grantedBy}` : 'Admin'}
      </td>
      <td className="dim">{grant.createdAt ? new Date(grant.createdAt).toLocaleDateString() : '—'}</td>
      <td className="actions">
        {!revoked && (
          <div className="button-row end">
            <button
              type="button"
              onClick={handleRevoke}
              className={confirming ? 'btn btn-sm btn-danger' : 'btn btn-sm'}
              disabled={loading}
            >
              {loading ? '…' : confirming ? 'Confirm Revoke' : 'Revoke'}
            </button>
            {confirming && !loading && (
              <button type="button" onClick={() => setConfirming(false)} className="btn btn-sm">
                Cancel
              </button>
            )}
          </div>
        )}
      </td>
    </tr>
  );
}

function MarkProductReleaseForm({ products }) {
  const [selectedProduct, setSelectedProduct] = useState('');
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const current = selectedProduct || products[0]?.id || '';

  async function handleSubmit(e) {
    e.preventDefault();
    if (!current) return;
    setError('');
    setResult(null);
    setLoading(true);
    try {
      const data = await markProductRelease(current);
      setResult(`Marked "${current}" as updated at ${new Date(data.releasedAt).toLocaleString()}`);
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
        {products.map((p) => (
          <label key={p.id} className={current === p.id ? 'chip active' : 'chip'}>
            <input
              type="radio"
              name="releaseProduct"
              value={p.id}
              checked={current === p.id}
              onChange={() => setSelectedProduct(p.id)}
            />
            {p.name}
          </label>
        ))}
      </div>
      {error && <p className="notice error">{error}</p>}
      {result && <p className="notice ok">{result}</p>}
      <button type="submit" className="btn btn-primary" disabled={loading || !current}>
        {loading ? 'Saving…' : 'Mark as New Release'}
      </button>
    </form>
  );
}

export default function AdminView() {
  const [products, setProducts] = useState([]);
  const [grants, setGrants] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [filter, setFilter] = useState('active');
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    getProducts()
      .then((data) => setProducts(Array.isArray(data) ? data : []))
      .catch(() => setProducts([]));
  }, []);

  useEffect(() => {
    let live = true;
    listGrants()
      .then((data) => {
        if (!live) return;
        setGrants(Array.isArray(data) ? data : []);
        setError('');
      })
      .catch((err) => { if (live) setError(errorText(err, 'Failed to load grants.')); })
      .finally(() => { if (live) setLoading(false); });
    return () => { live = false; };
  }, [reloadKey]);

  const reload = () => setReloadKey((k) => k + 1);

  async function handleRevoke(id) {
    try {
      await revokeGrant(id);
      reload();
    } catch (err) {
      setError(errorText(err, 'Failed to revoke.'));
    }
  }

  const filtered = grants.filter((g) => {
    if (filter === 'active') return g.status !== 'REVOKED';
    if (filter === 'revoked') return g.status === 'REVOKED';
    return true;
  });

  return (
    <section>
      <header className="page-head">
        <p className="kicker"><span className="tag">Command</span> Admin</p>
        <h1>Admin</h1>
      </header>

      <div className="split">
        <GrantForm products={products} onGranted={reload} />
        <MarkProductReleaseForm products={products} />
      </div>

      <section className="section">
        <div className="section-head">
          <h2>Given Products{!loading && ` (${filtered.length})`}</h2>
          <div className="chips">
            {['active', 'revoked', 'all'].map((f) => (
              <button
                key={f}
                type="button"
                onClick={() => setFilter(f)}
                className={filter === f ? 'chip active' : 'chip'}
              >
                {f}
              </button>
            ))}
            <button type="button" onClick={reload} className="chip">
              Refresh
            </button>
          </div>
        </div>

        {error && <p className="notice error">{error}</p>}

        {loading ? (
          <p className="notice">Loading…</p>
        ) : filtered.length === 0 ? (
          <p className="empty">Nothing given out yet.</p>
        ) : (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  {['Steam ID', 'Product', 'Status', 'Given by', 'Date', ''].map((h) => (
                    <th key={h} className={h === '' ? 'actions' : undefined}>
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {filtered.map((grant) => (
                  <GrantRow key={grant.id} grant={grant} onRevoke={handleRevoke} />
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
