export default function ShopView({
  authenticated,
  accountSetupComplete,
  premiumUser,
  subscriptionStatus,
  subscriptionId,
  subscriptionCancelAtPeriodEnd,
  subscriptionCurrentPeriodEnd,
  subscriptionCancelAt,
  shopLoading,
  products,
  orders,
  onStartSubscription,
  onCancelSubscription,
  onBuy,
  onDownload,
}) {
  return (
    <section style={{ marginTop: '2rem' }}>
      <h2 style={{ marginBottom: '0.25rem' }}>Shop</h2>
      <p style={{ marginTop: 0, opacity: 0.85 }}>
        Buy a package through Stripe Checkout. Orders are saved to your profile.
      </p>

      {authenticated && (
        <section style={{ marginTop: '0.75rem', marginBottom: '1rem' }}>
          {!accountSetupComplete && (
            <p style={{ margin: '0 0 0.5rem 0', color: '#b45309' }}>
              Account not setup. Link your Steam account before buying or starting subscription.
            </p>
          )}
          <p style={{ margin: 0 }}>
            Subscription status: <strong>{premiumUser ? 'Premium' : 'Standard'}</strong>
            {' '}({String(subscriptionStatus || 'none')})
          </p>
          {subscriptionId && (
            <p style={{ margin: '0.35rem 0 0 0', opacity: 0.85 }}>
              Subscription ID: <strong>{subscriptionId}</strong>
            </p>
          )}
          {subscriptionCancelAtPeriodEnd && subscriptionCurrentPeriodEnd && (
            <p style={{ margin: '0.35rem 0 0 0', opacity: 0.85 }}>
              Premium active until: <strong>{new Date(subscriptionCurrentPeriodEnd).toLocaleString()}</strong>
            </p>
          )}
          {subscriptionCancelAt && (
            <p style={{ margin: '0.35rem 0 0 0', opacity: 0.85 }}>
              Cancellation effective at: <strong>{new Date(subscriptionCancelAt).toLocaleString()}</strong>
            </p>
          )}
          {!premiumUser && (
            <button type="button" style={{ marginTop: '0.5rem' }} disabled={shopLoading || !accountSetupComplete} onClick={onStartSubscription}>
              Start Premium Subscription
            </button>
          )}
          {subscriptionId && !String(subscriptionStatus || '').toLowerCase().includes('canceled') && (
            <button
              type="button"
              style={{ marginTop: '0.5rem', marginLeft: premiumUser ? 0 : '0.5rem' }}
              disabled={shopLoading}
              onClick={onCancelSubscription}
            >
              Cancel Subscription
            </button>
          )}
        </section>
      )}

      <div style={{ marginTop: '1rem', display: 'grid', gap: '0.75rem' }}>
        {products.map((product) => (
          <article
            key={product.id}
            style={{ border: '1px solid #ddd', borderRadius: 8, padding: '0.75rem', maxWidth: 560 }}
          >
            <h3 style={{ margin: 0 }}>{product.name}</h3>
            <p style={{ marginTop: '0.5rem' }}>{product.description}</p>
            <p style={{ marginTop: '0.25rem' }}>
              <strong>
                {(product.amountCents / 100).toFixed(2)} {String(product.currency || '').toUpperCase()}
              </strong>
            </p>
            <button
              type="button"
              disabled={shopLoading || !accountSetupComplete}
              onClick={() => onBuy(product.id)}
            >
              Buy with Stripe
            </button>
          </article>
        ))}
        {!products.length && <p>No products configured.</p>}
      </div>

      {authenticated && (
        <section style={{ marginTop: '1.5rem' }}>
          <h3 style={{ marginBottom: '0.5rem' }}>Your Orders</h3>
          {!orders.length && !subscriptionId && <p>No orders yet.</p>}
          {subscriptionId && (
            <div style={{ marginBottom: '0.6rem' }}>
              Subscription · Premium Membership · {String(subscriptionStatus || '').toUpperCase() || 'NONE'}
              {subscriptionCancelAtPeriodEnd && subscriptionCurrentPeriodEnd
                ? ` · Active until ${new Date(subscriptionCurrentPeriodEnd).toLocaleString()}`
                : ''}
            </div>
          )}
          {orders.map((order) => (
            <div key={order.id} style={{ marginBottom: '0.6rem' }}>
              #{order.id} · {order.productName} · {order.status} · {(order.amountCents / 100).toFixed(2)}{' '}
              {String(order.currency || '').toUpperCase()}
              {order.status === 'PAID' && (
                <button
                  type="button"
                  style={{ marginLeft: '0.6rem' }}
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
