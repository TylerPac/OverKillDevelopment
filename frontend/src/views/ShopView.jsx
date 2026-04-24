export default function ShopView({
  authenticated,
  accountSetupComplete,
  shopLoading,
  products,
  orders,
  cart,
  onBuy,
  onDownload,
  onViewProduct,
  onAddToCart,
}) {
  return (
    <section style={{ marginTop: '1rem' }}>
      <h2>Shop</h2>
      <p style={{ color: '#888', marginBottom: '1rem' }}>
        Click a product to learn more, or add it to your cart.
      </p>

      {authenticated && !accountSetupComplete && (
        <section style={{
          background: '#1e1e2e', border: '1px solid #333', borderRadius: 8,
          padding: '0.85rem 1rem', marginBottom: '1.25rem', maxWidth: 560,
        }}>
          <p style={{ margin: 0, color: '#f4a261', fontSize: '0.85rem' }}>
            Account not set up. Link your Steam account before buying.
          </p>
        </section>
      )}

      <div style={{ display: 'grid', gap: '0.75rem' }}>
        {products.map((product) => {
          const inCart = cart.includes(product.id);
          return (
            <article
              key={product.id}
              onClick={() => onViewProduct(product.id)}
              onMouseEnter={(e) => { e.currentTarget.style.borderColor = '#4a4a7a'; }}
              onMouseLeave={(e) => { e.currentTarget.style.borderColor = '#333'; }}
              style={{
                background: '#1e1e2e', border: '1px solid #333',
                borderRadius: 8, padding: '1rem', maxWidth: 560,
                cursor: 'pointer', transition: 'border-color 0.15s',
              }}
            >
              <h3 style={{ marginBottom: '0.35rem' }}>{product.name}</h3>
              <p style={{ color: '#aaa', fontSize: '0.85rem', marginBottom: '0.5rem' }}>{product.description}</p>
              <p style={{ fontSize: '0.9rem', marginBottom: '0.75rem', color: '#cdf' }}>
                <strong>{(product.amountCents / 100).toFixed(2)} {String(product.currency || '').toUpperCase()}</strong>
              </p>
              <div style={{ display: 'flex', gap: '0.5rem' }}>
                <button
                  type="button"
                  disabled={inCart}
                  onClick={(e) => { e.stopPropagation(); onAddToCart(product.id); }}
                  style={{
                    background: inCart ? '#1e3a1e' : '#1e2a4a',
                    border: `1px solid ${inCart ? '#3a7a3a' : '#3a5a8e'}`,
                    color: inCart ? '#8f8' : '#adf',
                    borderRadius: 4, padding: '4px 12px',
                    cursor: inCart ? 'default' : 'pointer',
                    fontSize: '0.82rem', fontFamily: 'inherit',
                  }}
                >
                  {inCart ? '✓ In Cart' : '+ Add to Cart'}
                </button>
                <button
                  type="button"
                  onClick={(e) => { e.stopPropagation(); onViewProduct(product.id); }}
                  style={{ padding: '4px 12px', fontSize: '0.82rem' }}
                >
                  View Details →
                </button>
              </div>
            </article>
          );
        })}
        {!products.length && <p style={{ color: '#888' }}>No products configured.</p>}
      </div>

      {authenticated && (
        <section style={{ marginTop: '1.75rem' }}>
          <h3 style={{ marginBottom: '0.6rem' }}>Your Orders</h3>
          {!orders.length && <p style={{ color: '#888' }}>No orders yet.</p>}
          {orders.map((order) => (
            <div key={order.id} style={orderRowStyle}>
              <span style={{ color: '#888', fontSize: '0.82rem' }}>#{order.id}</span>
              {' '}&middot; {order.productName}
              {' '}&middot; <span style={{ color: order.status === 'PAID' ? '#8f8' : '#aaa' }}>{order.status}</span>
              {' '}&middot; {(order.amountCents / 100).toFixed(2)} {String(order.currency || '').toUpperCase()}
              {order.status === 'PAID' && (
                <button
                  type="button"
                  style={{ marginLeft: '0.6rem', padding: '2px 8px', fontSize: '0.78rem' }}
                  disabled={shopLoading}
                  onClick={() => onDownload(order.productId)}
                >
                  Download
                </button>
              )}
            </div>
          ))}
        </section>
      )}
    </section>
  );
}

const orderRowStyle = {
  padding: '0.45rem 0.6rem',
  marginBottom: '0.4rem',
  background: '#1a1a2e',
  borderRadius: 4,
  fontSize: '0.85rem',
  color: '#ccc',
  display: 'flex',
  alignItems: 'center',
  flexWrap: 'wrap',
  gap: '0.15rem',
};
