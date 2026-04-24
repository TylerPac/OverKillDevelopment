export default function CustomerView({ shopLoading, paidOrders, onDownload, onOpenTools }) {
  return (
    <section style={{ marginTop: '2rem' }}>
      <h2 style={{ marginBottom: '0.25rem' }}>Customer Downloads</h2>
      <p style={{ marginTop: 0, opacity: 0.85 }}>
        Download products from your paid purchases.
      </p>

      {shopLoading && <p>Loading your purchases...</p>}

      {!shopLoading && !paidOrders.length && (
        <p>No downloadable purchases found yet.</p>
      )}

      <div style={{ marginTop: '1rem', display: 'grid', gap: '0.75rem' }}>
        {paidOrders.map((order) => (
          <article
            key={`${order.id}-${order.productId}`}
            style={{ border: '1px solid #ddd', borderRadius: 8, padding: '0.75rem', maxWidth: 560 }}
          >
            <h3 style={{ margin: 0 }}>{order.productName}</h3>
            <p style={{ marginTop: '0.5rem', marginBottom: '0.5rem', opacity: 0.85 }}>
              Order #{order.id} · {(order.amountCents / 100).toFixed(2)} {String(order.currency || '').toUpperCase()}
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
                style={{ marginLeft: '0.5rem' }}
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
