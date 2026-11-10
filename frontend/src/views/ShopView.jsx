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
  showHeading = true,
}) {
  return (
    <section>
      {showHeading && (
        <header className="page-head">
          <p className="kicker"><span className="tag">Armory</span> OverKill Development</p>
          <h1>Shop</h1>
          <p>Click a product to learn more, or add it to your cart.</p>
        </header>
      )}
      {authenticated && !accountSetupComplete && (
        <p className="notice warn">Account not set up. Link your Steam account before buying.</p>
      )}

      <div className="product-grid">
        {products.map((product) => {
          const inCart = cart.includes(product.id);
          const owned = orders.some(
            (o) => o.productId === product.id && String(o.status || '').toUpperCase() === 'PAID'
          );
          return (
            <article
              key={product.id}
              onClick={() => onViewProduct(product.id)}
              className="product-card"
            >
              <div className="thumb">
                <img
                  src={`/products/${product.id}.png`}
                  alt=""
                  loading="lazy"
                  onError={(e) => { e.currentTarget.style.visibility = 'hidden'; }}
                />
                {owned && <span className="badge owned">OWNED</span>}
                {!owned && inCart && <span className="badge">IN CART</span>}
              </div>
              <div className="body">
                <h3>{product.name}</h3>
                <p className="desc">{product.description}</p>
                <div className="foot">
                  <span className="price">
                    {(product.amountCents / 100).toFixed(2)}
                    <small>{String(product.currency || '').toUpperCase()}</small>
                  </span>
                  <div className="button-row">
                    <button
                      type="button"
                      className="btn btn-sm"
                      onClick={(e) => { e.stopPropagation(); onViewProduct(product.id); }}
                    >
                      Details
                    </button>
                    <button
                      type="button"
                      disabled={inCart || owned}
                      onClick={(e) => { e.stopPropagation(); onAddToCart(product.id); }}
                      className={inCart || owned ? 'btn btn-sm btn-done' : 'btn btn-sm btn-primary'}
                    >
                      {owned ? '✓ Owned' : inCart ? '✓ In Cart' : '+ Add to Cart'}
                    </button>
                  </div>
                </div>
              </div>
            </article>
          );
        })}
      </div>
      {!products.length && <p className="empty">No products configured.</p>}

      {authenticated && (
        <section className="section">
          <div className="section-head">
            <h3>Your Orders</h3>
            <p>{orders.length} on record</p>
          </div>
          {!orders.length && <p className="empty">No orders yet.</p>}
          {orders.length > 0 && (
            <div className="rows">
              {orders.map((order) => (
                <div key={order.id} className="row">
                  <div className="row-main">
                    <strong>{order.productName}</strong>
                    <span className="row-meta">
                      #{order.id} · {(order.amountCents / 100).toFixed(2)} {String(order.currency || '').toUpperCase()}
                    </span>
                  </div>
                  <div className="button-row">
                    <span className={order.status === 'PAID' ? 'status ok' : 'status'}>{order.status}</span>
                    {order.status === 'PAID' && (
                      <button
                        type="button"
                        className="btn btn-sm"
                        disabled={shopLoading}
                        onClick={() => onDownload(order.productId)}
                      >
                        Download
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </section>
      )}
    </section>
  );
}
