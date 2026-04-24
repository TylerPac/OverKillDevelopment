export default function CartView({
  cart,
  products,
  onRemoveFromCart,
  onCheckoutCart,
  onBack,
  shopLoading,
  accountSetupComplete,
}) {
  const cartProducts = products.filter((p) => cart.includes(p.id));
  const totalCents = cartProducts.reduce((sum, p) => sum + p.amountCents, 0);
  const currency = cartProducts[0]?.currency ?? 'usd';

  return (
    <section style={{ marginTop: '1rem' }}>
      <h2>Cart</h2>

      {cartProducts.length === 0 ? (
        <div style={{ marginTop: '1rem' }}>
          <p style={{ color: '#888' }}>Your cart is empty.</p>
          <button type="button" onClick={onBack}>Browse Shop</button>
        </div>
      ) : (
        <>
          <div style={{ display: 'grid', gap: '0.75rem', maxWidth: 620 }}>
            {cartProducts.map((product) => {
              const price = `${(product.amountCents / 100).toFixed(2)} ${String(product.currency || '').toUpperCase()}`;
              return (
                <article
                  key={product.id}
                  style={{
                    background: '#1e1e2e', border: '1px solid #333',
                    borderRadius: 8, padding: '0.85rem 1rem',
                  }}
                >
                  <div style={{
                    display: 'flex', justifyContent: 'space-between',
                    alignItems: 'center', flexWrap: 'wrap', gap: '0.75rem',
                  }}>
                    <div>
                      <p style={{ margin: 0, fontWeight: 600, color: '#eee', fontSize: '0.95rem' }}>
                        {product.name}
                      </p>
                      <p style={{ margin: '0.2rem 0 0', color: '#cdf', fontSize: '0.9rem' }}>
                        {price}
                      </p>
                    </div>
                    <button
                      type="button"
                      onClick={() => onRemoveFromCart(product.id)}
                      style={{
                        background: 'transparent', border: '1px solid #633',
                        color: '#f88', borderRadius: 4, padding: '4px 10px',
                        fontSize: '0.82rem', cursor: 'pointer', fontFamily: 'inherit',
                      }}
                    >
                      Remove
                    </button>
                  </div>
                </article>
              );
            })}
          </div>

          {!accountSetupComplete && (
            <p style={{ color: '#f4a261', fontSize: '0.85rem', marginTop: '0.75rem' }}>
              Link your Steam account before purchasing.
            </p>
          )}

          {/* Cart total + single checkout */}
          <div style={{
            marginTop: '1.25rem', padding: '0.85rem 1rem',
            background: '#1e1e2e', border: '1px solid #333', borderRadius: 8,
            maxWidth: 620, display: 'flex', justifyContent: 'space-between',
            alignItems: 'center', flexWrap: 'wrap', gap: '0.75rem',
          }}>
            <div>
              <p style={{ margin: 0, color: '#aaa', fontSize: '0.82rem' }}>Total</p>
              <p style={{ margin: '0.15rem 0 0', color: '#cdf', fontWeight: 700, fontSize: '1.1rem' }}>
                {(totalCents / 100).toFixed(2)} {String(currency).toUpperCase()}
              </p>
            </div>
            <button
              type="button"
              disabled={shopLoading || !accountSetupComplete}
              onClick={onCheckoutCart}
              style={{ padding: '6px 20px', fontSize: '0.9rem' }}
            >
              Checkout All with Stripe
            </button>
          </div>

          <p style={{ color: '#555', fontSize: '0.78rem', marginTop: '0.5rem' }}>
            All items are processed in a single Stripe Checkout session.
          </p>
        </>
      )}
    </section>
  );
}
