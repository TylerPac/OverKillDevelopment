export default function CustomerView({ shopLoading, paidOrders, onDownload, onOpenTools }) {
  return (
    <section style={{ marginTop: '1rem' }}>
      <h2>Customer Downloads</h2>
      <p style={{ color: '#888', marginBottom: '1rem' }}>Download products from your paid purchases.</p>

      {shopLoading && <p style={{ color: '#aaa' }}>Loading your purchases...</p>}

      {!shopLoading && !paidOrders.length && (
        <p style={{ color: '#888' }}>No downloadable purchases found yet.</p>
      )}

      <div style={{ display: 'grid', gap: '0.75rem', marginTop: '0.5rem' }}>
        {paidOrders.map((order) => (
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
            <h3 style={{ marginBottom: '0.35rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              {order.productName}
              {order.hasUpdate && (
                <span style={{
                  background: '#1a4a2e',
                  border: '1px solid #2a7a4e',
                  borderRadius: 4,
                  color: '#4dde8a',
                  fontSize: '0.7rem',
                  fontWeight: 700,
                  letterSpacing: '0.05em',
                  padding: '0.15rem 0.45rem',
                  textTransform: 'uppercase',
                }}>
                  New Update
                </span>
              )}
            </h3>
            <p style={{ color: '#888', marginBottom: '0.75rem', fontSize: '0.85rem' }}>
              Order #{order.id} &middot; {(order.amountCents / 100).toFixed(2)} {String(order.currency || '').toUpperCase()}
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
          </article>
        ))}
      </div>
    </section>
  );
}
