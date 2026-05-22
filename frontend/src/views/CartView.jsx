import { useState } from 'react';

export default function CartView({
  cart,
  products,
  onRemoveFromCart,
  onCheckoutCart,
  onRedeemFullAccessCode,
  onBack,
  shopLoading,
  accountSetupComplete,
}) {
  const [accessCode, setAccessCode] = useState('');
  const [redeemMessage, setRedeemMessage] = useState(null);
  const [redeemSuccess, setRedeemSuccess] = useState(false);
  const cartProducts = products.filter((p) => cart.includes(p.id));
  const totalCents = cartProducts.reduce((sum, p) => sum + p.amountCents, 0);
  const currency = cartProducts[0]?.currency ?? 'usd';

  async function handleRedeemSubmit(event) {
    event.preventDefault();
    if (!accessCode.trim()) {
      return;
    }

    setRedeemMessage(null);
    const result = await onRedeemFullAccessCode(accessCode);
    setRedeemMessage(result.message);
    setRedeemSuccess(result.success);
    if (result.success) {
      setAccessCode('');
    }
  }

  return (
    <section style={{ marginTop: '1rem' }}>
      <h2>Cart</h2>

      <div
        style={{
          marginTop: '1rem',
          marginBottom: '1rem',
          maxWidth: 620,
          padding: '0.85rem 1rem',
          background: '#171725',
          border: '1px solid #2f3550',
          borderRadius: 8,
        }}
      >
        <p style={{ margin: 0, color: '#eee', fontWeight: 600 }}>
          Have a one-time access code?
        </p>
        <p style={{ margin: '0.35rem 0 0', color: '#9fb1d1', fontSize: '0.85rem' }}>
          Redeem it once to unlock your code-assigned products without going through Stripe.
        </p>

        <form
          onSubmit={handleRedeemSubmit}
          style={{ display: 'flex', gap: '0.65rem', flexWrap: 'wrap', marginTop: '0.9rem' }}
        >
          <input
            type="text"
            value={accessCode}
            onChange={(event) => setAccessCode(event.target.value.toUpperCase())}
            placeholder="OKD-ABCD-EFGH-IJKL"
            disabled={shopLoading}
            style={{
              flex: '1 1 280px',
              minWidth: 220,
              padding: '0.6rem 0.75rem',
              borderRadius: 6,
              border: '1px solid #44516d',
              background: '#0f1320',
              color: '#eee',
              fontFamily: 'inherit',
            }}
          />
          <button
            type="submit"
            disabled={shopLoading || !accountSetupComplete || !accessCode.trim()}
            style={{ padding: '0.6rem 1rem', fontSize: '0.9rem' }}
          >
            Redeem Code
          </button>
        </form>

        {!accountSetupComplete && (
          <p style={{ color: '#f4a261', fontSize: '0.8rem', margin: '0.65rem 0 0' }}>
            Link your Steam account before redeeming a code.
          </p>
        )}
        {redeemMessage && (
          <p style={{ color: redeemSuccess ? '#4dde8a' : '#f4a261', fontSize: '0.85rem', margin: '0.65rem 0 0' }}>
            {redeemMessage}
          </p>
        )}
      </div>

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
