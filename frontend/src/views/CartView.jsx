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
          <h3>Have a promo code?</h3>
          <p className="muted">
            Enter it on the Stripe checkout page, under &ldquo;Add promotion code&rdquo;. The discount shows before you pay.
          </p>
        </aside>
      </div>
    </section>
  );
}
