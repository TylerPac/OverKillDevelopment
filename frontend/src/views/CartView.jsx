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
    <section>
      <header className="page-head">
        <p className="kicker"><span className="tag">Loadout</span> {cartProducts.length} item{cartProducts.length === 1 ? '' : 's'}</p>
        <h1>Cart</h1>
      </header>

      <div className="split">
        <div>
          {cartProducts.length === 0 ? (
            <div className="empty">
              <p>Your cart is empty.</p>
              <button type="button" className="btn btn-primary" onClick={onBack}>Browse Shop</button>
            </div>
          ) : (
            <>
              <div className="rows">
                {cartProducts.map((product) => {
                  const price = `${(product.amountCents / 100).toFixed(2)} ${String(product.currency || '').toUpperCase()}`;
                  return (
                    <article key={product.id} className="row">
                      <div className="row-main">
                        <strong>{product.name}</strong>
                        <span className="row-meta">{price}</span>
                      </div>
                      <button
                        type="button"
                        className="btn btn-sm btn-danger"
                        onClick={() => onRemoveFromCart(product.id)}
                      >
                        Remove
                      </button>
                    </article>
                  );
                })}
              </div>

              {!accountSetupComplete && (
                <p className="notice warn mt-4">Link your Steam account before purchasing.</p>
              )}

              {/* Cart total + single checkout */}
              <div className="total-bar">
                <div>
                  <span className="label">Total</span>
                  <span className="price">
                    {(totalCents / 100).toFixed(2)}
                    <small>{String(currency).toUpperCase()}</small>
                  </span>
                </div>
                <button
                  type="button"
                  className="btn btn-primary btn-lg"
                  disabled={shopLoading || !accountSetupComplete}
                  onClick={onCheckoutCart}
                >
                  Checkout All with Stripe
                </button>
              </div>

              <p className="fine mt-4">
                All items are processed in a single Stripe Checkout session.
              </p>
            </>
          )}
        </div>

        <aside className="panel">
          <h3>Have a one-time access code?</h3>
          <p className="muted">
            Redeem it once to unlock your code-assigned products without going through Stripe.
          </p>

          <form onSubmit={handleRedeemSubmit} className="inline-form">
            <input
              type="text"
              value={accessCode}
              onChange={(event) => setAccessCode(event.target.value.toUpperCase())}
              placeholder="OKD-ABCD-EFGH-IJKL"
              disabled={shopLoading}
              aria-label="Access code"
            />
            <button
              type="submit"
              className="btn"
              disabled={shopLoading || !accountSetupComplete || !accessCode.trim()}
            >
              Redeem Code
            </button>
          </form>

          {!accountSetupComplete && (
            <p className="notice warn mt-4">Link your Steam account before redeeming a code.</p>
          )}
          {redeemMessage && (
            <p className={`notice mt-4 ${redeemSuccess ? 'ok' : 'warn'}`}>{redeemMessage}</p>
          )}
        </aside>
      </div>
    </section>
  );
}
