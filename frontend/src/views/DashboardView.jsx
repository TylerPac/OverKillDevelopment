import { useState } from 'react';

function RepoLinks({ repos }) {
  const [open, setOpen] = useState(false);

  if (!repos || repos.length === 0) return null;

  if (repos.length === 1) {
    return (
      <a
        href={repos[0]}
        target="_blank"
        rel="noopener noreferrer"
        style={{ display: 'inline-flex', alignItems: 'center', gap: '0.35rem', fontSize: '0.82rem', color: '#8cf', marginTop: '0.5rem' }}
      >
        <svg width="14" height="14" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
          <path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z" />
        </svg>
        View on GitHub
      </a>
    );
  }

  return (
    <div style={{ marginTop: '0.5rem' }}>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        style={{
          background: 'transparent',
          border: '1px solid #445',
          borderRadius: 4,
          color: '#8cf',
          fontSize: '0.82rem',
          padding: '0.2rem 0.55rem',
          cursor: 'pointer',
          display: 'inline-flex',
          alignItems: 'center',
          gap: '0.3rem',
        }}
      >
        <svg width="14" height="14" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
          <path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z" />
        </svg>
        GitHub Repos ({repos.length}) {open ? '▲' : '▼'}
      </button>
      {open && (
        <ul style={{ listStyle: 'none', margin: '0.4rem 0 0', padding: '0 0 0 0.25rem', display: 'flex', flexDirection: 'column', gap: '0.3rem' }}>
          {repos.map((url) => {
            const label = url.replace('https://github.com/', '');
            return (
              <li key={url}>
                <a
                  href={url}
                  target="_blank"
                  rel="noopener noreferrer"
                  style={{ fontSize: '0.82rem', color: '#8cf' }}
                >
                  {label}
                </a>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

export default function DashboardView({
  currentUser,
  discordUserId,
  discordUsername,
  githubUsername,
  githubReposByProduct,
  loading,
  authenticated,
  onStartDiscordLink,
  onStartGithubLink,
  // customer props
  shopLoading,
  paidOrders,
  onDownload,
  onOpenTools,
}) {
  const [tab, setTab] = useState('account');

  return (
    <section style={{ marginTop: '1rem' }}>
      <h2 style={{ marginBottom: '0.25rem' }}>Dashboard</h2>
      <p style={{ color: '#aaa', marginBottom: '1.25rem', marginTop: 0 }}>
        Signed in as <strong style={{ color: '#eee' }}>{currentUser || 'user'}</strong>
      </p>

      {/* Tab bar */}
      <div style={{ display: 'flex', gap: 0, borderBottom: '1px solid #333', marginBottom: '1.5rem' }}>
        {['account', 'downloads'].map((t) => (
          <button
            key={t}
            type="button"
            onClick={() => setTab(t)}
            style={{
              background: 'transparent',
              border: 'none',
              borderBottom: tab === t ? '2px solid #cdf' : '2px solid transparent',
              color: tab === t ? '#cdf' : '#888',
              padding: '0.5rem 1.25rem',
              cursor: 'pointer',
              fontFamily: 'inherit',
              fontSize: '0.9rem',
              fontWeight: tab === t ? 600 : 400,
              textTransform: 'capitalize',
              marginBottom: -1,
            }}
          >
            {t === 'downloads' ? `Downloads${paidOrders?.length ? ` (${paidOrders.length})` : ''}` : 'Account'}
          </button>
        ))}
      </div>

      {/* Account tab */}
      {tab === 'account' && (
        <div>
          <p style={{ color: '#aaa', marginTop: 0 }}>
            Discord:{' '}
            <strong style={{ color: '#eee' }}>{discordUserId ? discordUsername : 'not linked'}</strong>
          </p>
          <p style={{ color: '#aaa', marginTop: 0 }}>
            GitHub:{' '}
            <strong style={{ color: '#eee' }}>{githubUsername || 'not linked'}</strong>
          </p>
          <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
            <button type="button" disabled={loading || !authenticated} onClick={onStartDiscordLink}>
              {discordUserId ? 'Re-link Discord' : 'Link Discord'}
            </button>
            <button type="button" disabled={loading || !authenticated} onClick={onStartGithubLink}>
              {githubUsername ? 'Re-link GitHub' : 'Link GitHub'}
            </button>
          </div>
        </div>
      )}

      {/* Downloads tab */}
      {tab === 'downloads' && (
        <div>
          <p style={{ color: '#888', marginTop: 0, marginBottom: '1rem' }}>
            Download products from your paid purchases.
          </p>

          {shopLoading && <p style={{ color: '#aaa' }}>Loading your purchases...</p>}

          {!shopLoading && (!paidOrders || !paidOrders.length) && (
            <p style={{ color: '#888' }}>No downloadable purchases found yet.</p>
          )}

          <div style={{ display: 'grid', gap: '0.75rem' }}>
            {paidOrders?.map((order) => (
              <article
                key={`${order.id}-${order.productId}`}
                style={{
                  background: '#1e1e2e',
                  border: '1px solid #333',
                  borderRadius: 8,
                  padding: '1rem',
                  maxWidth: 560,
                }}
              >
                <h3 style={{ marginTop: 0, marginBottom: '0.35rem' }}>{order.productName}</h3>
                <p style={{ color: '#888', marginBottom: '0.75rem', marginTop: 0, fontSize: '0.85rem' }}>
                  Order #{order.id} &middot; {(order.amountCents / 100).toFixed(2)}{' '}
                  {String(order.currency || '').toUpperCase()}
                </p>
                <button
                  type="button"
                  disabled={shopLoading}
                  onClick={() => onDownload(order.productId)}
                >
                  Download
                </button>
                {onOpenTools && order.productId === 'keycard-crates' && (
                  <button
                    type="button"
                    disabled={shopLoading}
                    onClick={onOpenTools}
                    style={{ marginLeft: '0.5rem', background: '#2a3a5e' }}
                  >
                    Tools
                  </button>
                )}
                <div style={{ marginTop: '0.5rem' }}>
                  <RepoLinks repos={githubReposByProduct?.[order.productId]} />
                </div>
              </article>
            ))}
          </div>
        </div>
      )}
    </section>
  );
}
