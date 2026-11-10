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
        className="btn btn-sm"
      >
        <svg width="14" height="14" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
          <path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z" />
        </svg>
        View on GitHub
      </a>
    );
  }

  return (
    <div>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="btn btn-sm"
      >
        <svg width="14" height="14" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
          <path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z" />
        </svg>
        GitHub Repos ({repos.length}) {open ? '▲' : '▼'}
      </button>
      {open && (
        <ul className="repo-list">
          {repos.map((url) => {
            const label = url.replace('https://github.com/', '');
            return (
              <li key={url}>
                <a
                  href={url}
                  target="_blank"
                  rel="noopener noreferrer"
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
    <section>
      <header className="page-head">
        <p className="kicker"><span className="tag">Operator</span> Signed in</p>
        <h1>Dashboard</h1>
        <p>
          Signed in as <strong className="mono">{currentUser || 'user'}</strong>
        </p>
      </header>

      {/* Tab bar */}
      <div className="tabs" role="tablist">
        {['account', 'downloads'].map((t) => (
          <button
            key={t}
            type="button"
            role="tab"
            aria-selected={tab === t}
            onClick={() => setTab(t)}
            className={tab === t ? 'tab active' : 'tab'}
          >
            {t === 'downloads' ? `Downloads${paidOrders?.length ? ` (${paidOrders.length})` : ''}` : 'Account'}
          </button>
        ))}
      </div>

      {/* Account tab */}
      {tab === 'account' && (
        <div className="narrow">
          <div className="facts">
            <div className="fact">
              <span>Discord</span>
              <strong className={discordUserId ? '' : 'unset'}>{discordUserId ? discordUsername : 'not linked'}</strong>
            </div>
            <div className="fact">
              <span>GitHub</span>
              <strong className={githubUsername ? '' : 'unset'}>{githubUsername || 'not linked'}</strong>
            </div>
          </div>
          <div className="button-row">
            <button type="button" className="btn" disabled={loading || !authenticated} onClick={onStartDiscordLink}>
              {discordUserId ? 'Re-link Discord' : 'Link Discord'}
            </button>
            <button type="button" className="btn" disabled={loading || !authenticated} onClick={onStartGithubLink}>
              {githubUsername ? 'Re-link GitHub' : 'Link GitHub'}
            </button>
          </div>
        </div>
      )}

      {/* Downloads tab */}
      {tab === 'downloads' && (
        <div>
          <p className="muted">Download products from your paid purchases.</p>

          {shopLoading && <p className="notice">Loading your purchases...</p>}

          {!shopLoading && (!paidOrders || !paidOrders.length) && (
            <p className="empty">No downloadable purchases found yet.</p>
          )}

          <div className="product-grid">
            {paidOrders?.map((order) => (
              <article key={`${order.id}-${order.productId}`} className="panel panel-accent download-card">
                <h3>{order.productName}</h3>
                <p className="row-meta">
                  Order #{order.id} &middot; {(order.amountCents / 100).toFixed(2)}{' '}
                  {String(order.currency || '').toUpperCase()}
                </p>
                <div className="button-row">
                  <button
                    type="button"
                    className="btn btn-primary"
                    disabled={shopLoading}
                    onClick={() => onDownload(order.productId)}
                  >
                    Download
                  </button>
                  {onOpenTools && order.productId === 'keycard-crates' && (
                    <button
                      type="button"
                      className="btn"
                      disabled={shopLoading}
                      onClick={onOpenTools}
                    >
                      Tools
                    </button>
                  )}
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
