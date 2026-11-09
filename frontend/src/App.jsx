import { useEffect, useMemo, useState } from 'react';
import useSessionState from './hooks/useSessionState';
import { getDiscordLinkUrl, getGithubLinkUrl, SIGN_IN_PATH, signOutOfServer } from './services/authService';
import { apiBase, callProtectedText, issueDownloadToken, getProductDownloadLink } from './services/apiClient';
import {
  createCartCheckoutSession,
  createCheckoutSession,
  createSubscriptionCheckoutSession,
  getOrders,
  getProducts,
  redeemFullAccessCode,
  syncCheckoutFromSession,
} from './services/shopService';
import AdminView from './views/AdminView';
import AuthView from './views/AuthView';
import CartView from './views/CartView';
import DashboardView from './views/DashboardView';
import HomeView from './views/HomeView';
import { PrivacyView, RefundView, TermsView } from './views/PolicyViews';
import ProductDetailView from './views/ProductDetailView';
import ShopView from './views/ShopView';
import TierPainterView from './views/TierPainterView';

const ADMIN_STEAM_ID = '76561199155324762';

export default function App() {
  const [view, setView] = useState('home');
  const [status, setStatus] = useState('Sign in with Steam to access your dashboard and purchases.');
  const [protectedData, setProtectedData] = useState('No protected request made yet.');
  const [loading, setLoading] = useState(false);
  const [shopLoading, setShopLoading] = useState(false);
  const [products, setProducts] = useState([]);
  const [orders, setOrders] = useState([]);
  const [cart, setCart] = useState([]);
  const [selectedProduct, setSelectedProduct] = useState(null);

  const session = useSessionState((message) => {
    setProtectedData('No protected request made yet.');
    setOrders([]);
    setView('home');
    setStatus(message);
  });

  const isAdmin = session.steam64Id === ADMIN_STEAM_ID;


  // Ensure products are loaded on home and shop views
  useEffect(() => {
    if ((view === 'home' || view === 'shop') && products.length === 0 && !shopLoading) {
      loadShopData();
    }
  }, [view, products.length, shopLoading]);

  const paidOrders = useMemo(
    () => orders.filter((order) => String(order.status || '').toUpperCase() === 'PAID'),
    [orders],
  );

  async function loadShopData() {
    setShopLoading(true);
    try {
      const nextProducts = await getProducts();
      setProducts(Array.isArray(nextProducts) ? nextProducts : []);

      if (!session.authenticated) {
        setOrders([]);
        return;
      }

      const nextOrders = await getOrders();
      setOrders(Array.isArray(nextOrders) ? nextOrders : []);

      await session.loadAuthProfile();
    } catch (error) {
      setStatus(`Shop load failed: ${error.message}`);
    } finally {
      setShopLoading(false);
    }
  }

  async function loadCustomerData() {
    if (!session.authenticated) {
      setOrders([]);
      return;
    }

    setShopLoading(true);
    try {
      const nextOrders = await getOrders();
      setOrders(Array.isArray(nextOrders) ? nextOrders : []);

      await session.loadAuthProfile();
    } catch (error) {
      setStatus(`Customer page load failed: ${error.message}`);
    } finally {
      setShopLoading(false);
    }
  }

  useEffect(() => {
    if (window.location.pathname !== '/steam-callback' || !session.ready) {
      return;
    }

    const failed = new URLSearchParams(window.location.search).has('error');
    window.history.replaceState({}, '', '/');

    if (failed || !session.authenticated) {
      setView('auth');
      setStatus('Steam sign-in failed. Please try again with your Steam account.');
      return;
    }

    setView('dashboard');
    setStatus(`Signed in with Steam as ${session.currentUser}.`);
    getOrders()
      .then((nextOrders) => setOrders(Array.isArray(nextOrders) ? nextOrders : []))
      .catch(() => undefined); // Non-fatal: the dashboard will just show no orders.
  }, [session.ready, session.authenticated, session.currentUser]);

  useEffect(() => {
    const path = window.location.pathname;
    if (path !== '/discord-callback' || !session.ready) {
      return;
    }

    const search = new URLSearchParams(window.location.search);
    const statusFromUrl = search.get('status');
    const errorFromUrl = search.get('error');
    const discordUsernameFromUrl = search.get('discordUsername') || '';

    async function completeCallback() {
      if (!session.authenticated) {
        setView('auth');
        setStatus('Discord link failed: sign in first.');
        window.history.replaceState({}, '', '/');
        return;
      }

      if (errorFromUrl) {
        setView('dashboard');
        setStatus(`Discord link failed: ${errorFromUrl}`);
        window.history.replaceState({}, '', '/');
        return;
      }

      if (statusFromUrl !== 'discord_linked') {
        setView('dashboard');
        setStatus('Discord link failed: missing callback data.');
        window.history.replaceState({}, '', '/');
        return;
      }

      setLoading(true);
      try {
        await session.loadAuthProfile();
        setView('dashboard');
        setStatus(
          discordUsernameFromUrl
            ? `Discord account linked successfully: ${discordUsernameFromUrl}.`
            : 'Discord account linked successfully.',
        );
      } catch (error) {
        setView('dashboard');
        setStatus(`Discord link failed: ${error.message}`);
      } finally {
        setLoading(false);
        window.history.replaceState({}, '', '/');
      }
    }

    completeCallback();
  }, [session]);

  useEffect(() => {
    const path = window.location.pathname;
    if (path !== '/github-callback' || !session.ready) {
      return;
    }

    const search = new URLSearchParams(window.location.search);
    const statusFromUrl = search.get('status');
    const errorFromUrl = search.get('error');
    const githubUsernameFromUrl = search.get('githubUsername') || '';

    async function completeCallback() {
      if (!session.authenticated) {
        setView('auth');
        setStatus('GitHub link failed: sign in first.');
        window.history.replaceState({}, '', '/');
        return;
      }

      if (errorFromUrl) {
        setView('dashboard');
        setStatus(`GitHub link failed: ${errorFromUrl}`);
        window.history.replaceState({}, '', '/');
        return;
      }

      if (statusFromUrl !== 'github_linked') {
        setView('dashboard');
        setStatus('GitHub link failed: missing callback data.');
        window.history.replaceState({}, '', '/');
        return;
      }

      setLoading(true);
      try {
        await session.loadAuthProfile();
        setView('dashboard');
        setStatus(
          githubUsernameFromUrl
            ? `GitHub account linked successfully: ${githubUsernameFromUrl}.`
            : 'GitHub account linked successfully.',
        );
      } catch (error) {
        setView('dashboard');
        setStatus(`GitHub link failed: ${error.message}`);
      } finally {
        setLoading(false);
        window.history.replaceState({}, '', '/');
      }
    }

    completeCallback();
  }, [session]);

  useEffect(() => {
    const search = new URLSearchParams(window.location.search);
    const checkout = search.get('checkout');
    const sessionId = search.get('session_id');
    if (!checkout || !session.ready) {
      return;
    }

    let active = true;

    async function handleCheckoutReturn() {
      setView('shop');

      if (checkout === 'success') {
        setStatus('Payment completed. Refreshing your order...');

        if (session.authenticated && sessionId) {
          try {
            await syncCheckoutFromSession(sessionId);
          } catch (error) {
            if (active) {
              setStatus(`Payment succeeded, but order sync failed: ${error.message}`);
            }
          }
        }

        if (active) {
          // Force the existing shop load effect to refetch orders and products.
          setProducts([]);
        }
      } else if (checkout === 'cancel') {
        setStatus('Checkout canceled. No charge was made.');
      }

      window.history.replaceState({}, '', '/');
    }

    handleCheckoutReturn();
    return () => {
      active = false;
    };
  }, [session.ready, session.authenticated]);

  async function startSteamSignIn() {
    setLoading(true);
    setStatus('Redirecting to Steam...');
    window.location.assign(SIGN_IN_PATH);
  }

  async function startDiscordLink() {
    if (!session.authenticated) {
      setView('auth');
      setStatus('Sign in with Steam before linking Discord.');
      return;
    }

    setLoading(true);
    setStatus('Redirecting to Discord...');
    try {
      const url = await getDiscordLinkUrl();
      window.location.assign(url);
    } catch (error) {
      setStatus(`Discord link failed: ${error.message}`);
      setLoading(false);
    }
  }

  async function startGithubLink() {
    if (!session.authenticated) {
      setView('auth');
      setStatus('Sign in with Steam before linking GitHub.');
      return;
    }

    setLoading(true);
    setStatus('Redirecting to GitHub...');
    try {
      const url = await getGithubLinkUrl();
      window.location.assign(url);
    } catch (error) {
      setStatus(`GitHub link failed: ${error.message}`);
      setLoading(false);
    }
  }

  async function handleCartCheckout() {
    if (!session.authenticated) {
      setStatus('Please log in before purchasing.');
      setView('auth');
      return;
    }

    if (!session.accountSetupComplete) {
      setStatus('Account not setup. Link your Steam account before buying.');
      return;
    }

    if (cart.length === 0) return;

    setShopLoading(true);
    setStatus('Creating Stripe checkout session...');

    try {
      const idempotencyKey = `cart-${cart.slice().sort().join('-')}-${Date.now()}`;
      const response = await createCartCheckoutSession(cart, idempotencyKey);

      if (!response?.checkoutUrl) {
        throw new Error('No checkout URL returned by backend');
      }

      window.location.assign(response.checkoutUrl);
    } catch (error) {
      if (error.message === 'account_not_setup') {
        setStatus('Account not setup. Link your Steam account before buying.');
      } else {
        setStatus(`Checkout failed: ${error.message}`);
      }
      setShopLoading(false);
    }
  }

  async function handleRedeemFullAccessCode(code) {
    if (!session.authenticated) {
      setStatus('Please log in before redeeming a code.');
      setView('auth');
      return { success: false, message: 'Please log in before redeeming a code.' };
    }

    if (!session.accountSetupComplete) {
      return { success: false, message: 'Link your Steam account before redeeming a code.' };
    }

    setShopLoading(true);

    try {
      const response = await redeemFullAccessCode(code);
      const nextOrders = await getOrders();
      setOrders(Array.isArray(nextOrders) ? nextOrders : []);
      await session.loadAuthProfile();
      setCart([]);

      const grantedCount = Number(response?.grantedCount || 0);
      const msg =
        grantedCount > 0
          ? `Code redeemed. ${grantedCount} product${grantedCount === 1 ? '' : 's'} unlocked.`
          : 'Code redeemed.';
      setStatus(msg);
      return { success: true, message: msg };
    } catch (error) {
      let msg;
      switch (error.message) {
        case 'access_code_required':
          msg = 'Enter a code before redeeming.';
          break;
        case 'invalid_access_code':
          msg = 'That code was not recognized.';
          break;
        case 'access_code_already_redeemed':
          msg = 'That code has already been used.';
          break;
        case 'nothing_to_redeem':
          msg = 'Your account already owns every product — nothing left to unlock.';
          break;
        case 'account_not_setup':
          msg = 'Link your Steam account before redeeming a code.';
          break;
        default:
          msg = `Code redeem failed: ${error.message}`;
          break;
      }
      setStatus(msg);
      return { success: false, message: msg };
    } finally {
      setShopLoading(false);
    }
  }

  async function handleBuy(productId) {
    if (!session.authenticated) {
      setStatus('Please log in before purchasing.');
      setView('auth');
      return;
    }

    if (!session.accountSetupComplete) {
      setStatus('Account not setup. Link your Steam account before buying.');
      return;
    }

    setShopLoading(true);
    setStatus('Creating Stripe checkout session...');

    try {
      const idempotencyKey = `checkout-${productId}-${Date.now()}`;
      const response = await createCheckoutSession(productId, idempotencyKey);

      if (!response?.checkoutUrl) {
        throw new Error('No checkout URL returned by backend');
      }

      window.location.assign(response.checkoutUrl);
    } catch (error) {
      if (error.message === 'account_not_setup') {
        setStatus('Account not setup. Link your Steam account before buying.');
      } else {
        setStatus(`Checkout failed: ${error.message}`);
      }
      setShopLoading(false);
    }
  }

  async function handleStartSubscription() {
    if (!session.authenticated) {
      setStatus('Please log in before subscribing.');
      setView('auth');
      return;
    }

    if (!session.accountSetupComplete) {
      setStatus('Account not setup. Link your Steam account before subscribing.');
      return;
    }

    setShopLoading(true);
    setStatus('Creating Stripe subscription checkout session...');

    try {
      const idempotencyKey = `subscription-premium-${Date.now()}`;
      const response = await createSubscriptionCheckoutSession(idempotencyKey);

      if (!response?.checkoutUrl) {
        throw new Error('No checkout URL returned by backend');
      }

      window.location.assign(response.checkoutUrl);
    } catch (error) {
      if (error.message === 'account_not_setup') {
        setStatus('Account not setup. Link your Steam account before subscribing.');
      } else {
        setStatus(`Subscription checkout failed: ${error.message}`);
      }
      setShopLoading(false);
    }
  }

  async function handleDownload(productId) {
    if (!session.authenticated) {
      setStatus('Please log in before downloading.');
      setView('auth');
      return;
    }

    setShopLoading(true);
    setStatus('Preparing your download...');

    try {
      // Try link-based download first (e.g. external URL configured per product)
      const linkUrl = await getProductDownloadLink(productId);
      if (linkUrl) {
        window.open(linkUrl, '_blank', 'noopener,noreferrer');
        setStatus('Opening download link...');
        return;
      }

      // Get a short-lived token and let the browser handle the download natively
      const dlToken = await issueDownloadToken(productId);
      window.location.href = `${apiBase}/shop/download-stream?token=${encodeURIComponent(dlToken)}`;

      // Refresh orders shortly after so the "New Update" badge clears
      setTimeout(async () => {
        try {
          const nextOrders = await getOrders();
          setOrders(Array.isArray(nextOrders) ? nextOrders : []);
        } catch (_) {
          // non-critical — badge will clear on next page load
        }
      }, 3000);

      setStatus('Download started.');
    } catch (error) {
      if (error.message === 'purchase_required') {
        setStatus('Download failed: purchase this product first.');
      } else if (error.message === 'account_not_setup') {
        setStatus('Download failed: link your Steam account first.');
      } else if (error.message === 'download_not_found') {
        setStatus('Download failed: file is not available yet.');
      } else {
        setStatus(`Download failed: ${error.message}`);
      }
    } finally {
      setShopLoading(false);
    }
  }

  async function handleProtectedRequest(path) {
    if (!session.authenticated) {
      setStatus('Please sign in with Steam first.');
      return;
    }

    setLoading(true);
    setStatus(`Calling ${path} ...`);

    try {
      const data = await callProtectedText(path);
      setProtectedData(data);
      setStatus(`Protected request to ${path} succeeded.`);
    } catch (error) {
      if (String(error.message).includes('401')) {
        session.clearSession();
        setProtectedData('No protected request made yet.');
        setOrders([]);
        setView('home');
        setStatus('Session is invalid or expired. Please sign in again.');
      } else {
        setStatus(`Protected request failed: ${error.message}`);
      }
    } finally {
      setLoading(false);
    }
  }

  async function signOut() {
    try {
      window.location.assign(await signOutOfServer());
    } catch (error) {
      setStatus(`Sign-out failed: ${error.message}`);
    }
  }

  function openAuth() {
    setView('auth');
  }

  function openShop() {
    setView('shop');
    loadShopData();
  }

  function openProduct(productId) {
    const product = products.find((p) => p.id === productId);
    if (!product) return;
    setSelectedProduct(product);
    setView('product-detail');
  }

  function addToCart(productId) {
    setCart((prev) => (prev.includes(productId) ? prev : [...prev, productId]));
  }

  function removeFromCart(productId) {
    setCart((prev) => prev.filter((id) => id !== productId));
  }

  function openPolicy(viewName) {
    setView(viewName);
  }

  function openCustomer() {
    if (!session.authenticated) {
      setStatus('Please log in to access customer downloads.');
      setView('auth');
      return;
    }

    setView('dashboard');
    loadCustomerData();
  }

  if (view === 'tier-painter' && session.authenticated) {
    return <TierPainterView onBack={() => setView('dashboard')} />;
  }

  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', background: '#111', color: '#eee' }}>
      {/* ── Top nav bar ──────────────────────────────────────────────── */}
      <header style={{
        display: 'flex', justifyContent: 'space-between', alignItems: 'center',
        padding: '0 1.5rem', height: 48, background: '#1e1e2e',
        borderBottom: '1px solid #333', flexShrink: 0,
      }}>
        <button
          type="button"
          onClick={() => setView('home')}
          style={{ background: 'transparent', border: 'none', color: '#cdf', fontWeight: 700, fontSize: '0.95rem', padding: '0 4px', cursor: 'pointer' }}
        >
          OverKill Development
        </button>

        <nav style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
          <button type="button" onClick={openShop} style={navBtnStyle}>Shop</button>
          <button type="button" onClick={() => setView('cart')} style={navBtnStyle}>
            Cart{cart.length > 0 ? ` (${cart.length})` : ''}
          </button>
          {session.authenticated ? (
            <>
              <button type="button" onClick={openCustomer} style={navBtnStyle}>Dashboard</button>
              {isAdmin && (
                <button type="button" onClick={() => setView('admin')} style={{ ...navBtnStyle, color: '#fda', borderColor: '#643' }}>Admin</button>
              )}
              <button type="button" onClick={signOut} style={{ ...navBtnStyle, color: '#f88', borderColor: '#633' }}>Log Out</button>
            </>
          ) : (
            <button type="button" onClick={openAuth} style={{ ...navBtnStyle, color: '#8f8', borderColor: '#363' }}>Sign In</button>
          )}
        </nav>
      </header>

      {/* ── Page content ─────────────────────────────────────────────── */}
      <main style={{ flex: 1, maxWidth: 860, width: '100%', margin: '0 auto', padding: '1.75rem 1.5rem' }}>

      {view === 'home' && (
        <HomeView
          authenticated={session.authenticated}
          accountSetupComplete={session.accountSetupComplete}
          shopLoading={shopLoading}
          products={products}
          orders={orders}
          cart={cart}
          onBuy={handleBuy}
          onDownload={handleDownload}
          onViewProduct={openProduct}
          onAddToCart={addToCart}
        />
      )}
      {view === 'auth' && !session.authenticated && (
        <AuthView loading={loading} status={status} onStartSteamSignIn={startSteamSignIn} />
      )}
      {view === 'dashboard' && session.authenticated && (
        <DashboardView
          currentUser={session.currentUser}
          discordUserId={session.discordUserId}
          discordUsername={session.discordUsername}
          githubUsername={session.githubUsername}
          githubReposByProduct={session.githubReposByProduct}
          loading={loading}
          authenticated={session.authenticated}
          onStartDiscordLink={startDiscordLink}
          onStartGithubLink={startGithubLink}
          shopLoading={shopLoading}
          paidOrders={paidOrders}
          onDownload={handleDownload}
          onOpenTools={() => setView('tier-painter')}
        />
      )}
      {view === 'shop' && (
        <ShopView
          authenticated={session.authenticated}
          accountSetupComplete={session.accountSetupComplete}
          shopLoading={shopLoading}
          products={products}
          orders={orders}
          cart={cart}
          onBuy={handleBuy}
          onDownload={handleDownload}
          onViewProduct={openProduct}
          onAddToCart={addToCart}
        />
      )}
      {view === 'product-detail' && selectedProduct && (
        <ProductDetailView
          product={selectedProduct}
          cart={cart}
          onAddToCart={addToCart}
          onBuy={handleBuy}
          onBack={() => setView('shop')}
          shopLoading={shopLoading}
          accountSetupComplete={session.accountSetupComplete}
        />
      )}
      {view === 'cart' && (
        <CartView
          cart={cart}
          products={products}
          onRemoveFromCart={removeFromCart}
          onBuy={handleBuy}
          onCheckoutCart={handleCartCheckout}
          onRedeemFullAccessCode={handleRedeemFullAccessCode}
          onBack={openShop}
          shopLoading={shopLoading}
          accountSetupComplete={session.accountSetupComplete}
        />
      )}
      {view === 'admin' && isAdmin && (
        <AdminView />
      )}
      {view === 'terms' && <TermsView />}
      {view === 'privacy' && <PrivacyView />}
      {view === 'refund' && <RefundView />}

      {view === 'dashboard' && !session.authenticated && (
        <section style={{ marginTop: '1.5rem' }}>
          <p>Please sign in with Steam to access the dashboard.</p>
          <button type="button" onClick={openAuth}>Go to Sign In</button>
        </section>
      )}

      {view === 'customer' && !session.authenticated && (
        <section style={{ marginTop: '1.5rem' }}>
          <p>Please sign in with Steam to access customer downloads.</p>
          <button type="button" onClick={openAuth}>Go to Sign In</button>
        </section>
      )}

      </main>

      {/* ── Footer ───────────────────────────────────────────────────── */}
      <footer style={{
        borderTop: '1px solid #2a2a3e', padding: '0.6rem 1.5rem',
        display: 'flex', gap: '0.5rem', flexWrap: 'wrap', background: '#1e1e2e',
        flexShrink: 0,
      }}>
        <button type="button" onClick={() => openPolicy('terms')} style={navBtnStyle}>Terms</button>
        <button type="button" onClick={() => openPolicy('privacy')} style={navBtnStyle}>Privacy</button>
        <button type="button" onClick={() => openPolicy('refund')} style={navBtnStyle}>Refund</button>
      </footer>
    </div>
  );
}

const navBtnStyle = {
  background: 'transparent',
  border: '1px solid #444',
  color: '#ccc',
  borderRadius: 4,
  padding: '3px 10px',
  cursor: 'pointer',
  fontSize: '0.8rem',
  fontFamily: 'inherit',
};
