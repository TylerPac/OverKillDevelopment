import { useEffect, useMemo, useState } from 'react';
import useSessionState from './hooks/useSessionState';
import { getDiscordLinkUrl, getSteamLoginUrl } from './services/authService';
import { callProtectedText, downloadProductBlob } from './services/apiClient';
import {
  cancelSubscription,
  createCheckoutSession,
  createSubscriptionCheckoutSession,
  getOrders,
  getProducts,
  getSubscriptionStatus,
  syncSubscriptionFromSession,
} from './services/shopService';
import { boolFromString } from './utils/authToken';
import AuthView from './views/AuthView';
import CustomerView from './views/CustomerView';
import DashboardView from './views/DashboardView';
import HomeView from './views/HomeView';
import { PrivacyView, RefundView, TermsView } from './views/PolicyViews';
import ShopView from './views/ShopView';
import TierPainterView from './views/TierPainterView';

export default function App() {
  const [view, setView] = useState('home');
  const [status, setStatus] = useState('Sign in with Steam to access your dashboard and purchases.');
  const [protectedData, setProtectedData] = useState('No protected request made yet.');
  const [loading, setLoading] = useState(false);
  const [shopLoading, setShopLoading] = useState(false);
  const [products, setProducts] = useState([]);
  const [orders, setOrders] = useState([]);

  const session = useSessionState((message) => {
    setProtectedData('No protected request made yet.');
    setOrders([]);
    setView('home');
    setStatus(message);
  });

  const paidOrders = useMemo(
    () => orders.filter((order) => String(order.status || '').toUpperCase() === 'PAID'),
    [orders],
  );

  async function loadShopData() {
    setShopLoading(true);
    try {
      const nextProducts = await getProducts();
      setProducts(Array.isArray(nextProducts) ? nextProducts : []);

      if (!session.authenticated || !session.token) {
        setOrders([]);
        return;
      }

      const nextOrders = await getOrders(session.token);
      setOrders(Array.isArray(nextOrders) ? nextOrders : []);

      const nextSubscription = await getSubscriptionStatus(session.token);
      session.applySubscriptionState(nextSubscription);
      await session.loadAuthProfile(session.token);
    } catch (error) {
      setStatus(`Shop load failed: ${error.message}`);
    } finally {
      setShopLoading(false);
    }
  }

  async function loadCustomerData() {
    if (!session.authenticated || !session.token) {
      setOrders([]);
      return;
    }

    setShopLoading(true);
    try {
      const nextOrders = await getOrders(session.token);
      setOrders(Array.isArray(nextOrders) ? nextOrders : []);

      const nextSubscription = await getSubscriptionStatus(session.token);
      session.applySubscriptionState(nextSubscription);
      await session.loadAuthProfile(session.token);
    } catch (error) {
      setStatus(`Customer page load failed: ${error.message}`);
    } finally {
      setShopLoading(false);
    }
  }

  useEffect(() => {
    const path = window.location.pathname;
    if (path !== '/steam-callback') {
      return;
    }

    const search = new URLSearchParams(window.location.search);
    const tokenFromUrl = search.get('token');
    const refreshFromUrl = search.get('refreshToken') || '';
    const usernameFromUrl = search.get('username') || '';
    const premiumFromUrl = boolFromString(search.get('premiumUser'), false);
    const subscriptionFromUrl = search.get('subscriptionStatus') || 'none';

    async function completeSteamCallback() {
      if (!tokenFromUrl || !usernameFromUrl) {
        setView('auth');
        setStatus('Steam sign-in failed: missing callback data.');
        window.history.replaceState({}, '', '/');
        return;
      }

      session.saveSession(
        {
          token: tokenFromUrl,
          refreshToken: refreshFromUrl,
          premiumUser: premiumFromUrl,
          subscriptionStatus: subscriptionFromUrl,
          emailVerified: true,
          accountSetupComplete: true,
        },
        usernameFromUrl,
      );

      try {
        await session.loadAuthProfile(tokenFromUrl);
      } catch {
        // Keep the sign-in successful even if the profile refresh fails.
      }

      setView('dashboard');
      setStatus(`Signed in with Steam as ${usernameFromUrl}.`);
      window.history.replaceState({}, '', '/');
    }

    completeSteamCallback();
  }, [session]);

  useEffect(() => {
    const path = window.location.pathname;
    if (path !== '/discord-callback') {
      return;
    }

    const search = new URLSearchParams(window.location.search);
    const statusFromUrl = search.get('status');
    const errorFromUrl = search.get('error');
    const discordUsernameFromUrl = search.get('discordUsername') || '';

    async function completeCallback() {
      if (!session.token) {
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
        await session.loadAuthProfile(session.token);
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
    const search = new URLSearchParams(window.location.search);
    const checkout = search.get('checkout');
    const subscription = search.get('subscription');
    const checkoutSessionId = search.get('session_id');
    if (!checkout && !subscription) {
      return;
    }

    setView('shop');
    if (checkout === 'success') {
      setStatus('Payment completed. Stripe webhook will update your order status shortly.');
    } else if (checkout === 'cancel') {
      setStatus('Checkout canceled. No charge was made.');
    } else if (subscription === 'success') {
      setStatus('Subscription checkout completed. Premium status updates after webhook confirmation.');
    } else if (subscription === 'cancel') {
      setStatus('Subscription checkout canceled.');
    }

    async function syncIfNeeded() {
      if (subscription !== 'success' || !checkoutSessionId || !session.token) {
        return;
      }

      try {
        await syncSubscriptionFromSession(session.token, checkoutSessionId);
        await loadShopData();
        setStatus('Subscription activated. Your account is now premium.');
      } catch (error) {
        setStatus(`Subscription checkout completed but sync failed: ${error.message}`);
      }
    }

    syncIfNeeded();
    window.history.replaceState({}, '', '/');
  }, [session]);

  async function startSteamSignIn() {
    setLoading(true);
    setStatus('Redirecting to Steam...');
    try {
      const url = await getSteamLoginUrl();
      window.location.assign(url);
    } catch (error) {
      setStatus(`Steam sign-in failed: ${error.message}`);
      setLoading(false);
    }
  }

  async function startDiscordLink() {
    if (!session.authenticated || !session.token) {
      setView('auth');
      setStatus('Sign in with Steam before linking Discord.');
      return;
    }

    setLoading(true);
    setStatus('Redirecting to Discord...');
    try {
      const url = await getDiscordLinkUrl(session.token);
      window.location.assign(url);
    } catch (error) {
      setStatus(`Discord link failed: ${error.message}`);
      setLoading(false);
    }
  }

  async function handleBuy(productId) {
    if (!session.authenticated || !session.token) {
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
      const response = await createCheckoutSession(productId, session.token, idempotencyKey);

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
    if (!session.authenticated || !session.token) {
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
      const response = await createSubscriptionCheckoutSession(session.token, idempotencyKey);

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
    if (!session.authenticated || !session.token) {
      setStatus('Please log in before downloading.');
      setView('auth');
      return;
    }

    setShopLoading(true);
    setStatus('Preparing your download...');

    try {
      const { blob, fileName } = await downloadProductBlob(productId, session.token);

      const objectUrl = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = objectUrl;
      link.download = fileName;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(objectUrl);

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

  async function handleCancelSubscription() {
    if (!session.authenticated || !session.token) {
      setStatus('Please log in before canceling subscription.');
      return;
    }

    setShopLoading(true);
    setStatus('Canceling your subscription...');

    try {
      await cancelSubscription(session.token);
      await loadShopData();
      setStatus('Subscription will cancel at period end. Premium remains active until then.');
    } catch (error) {
      if (error.message === 'subscription_not_found') {
        setStatus('No active subscription found.');
      } else {
        setStatus(`Cancel subscription failed: ${error.message}`);
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
    setStatus(`Calling ${path} with JWT...`);

    try {
      const data = await callProtectedText(path, session.token);
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

  function signOut() {
    session.clearSession();
    setProtectedData('No protected request made yet.');
    setOrders([]);
    setView('home');
    setStatus('Signed out.');
  }

  function openAuth() {
    setView('auth');
  }

  function openShop() {
    setView('shop');
    loadShopData();
  }

  function openPolicy(viewName) {
    setView(viewName);
  }

  function openCustomer() {
    if (!session.authenticated || !session.token) {
      setStatus('Please log in to access customer downloads.');
      setView('auth');
      return;
    }

    setView('customer');
    loadCustomerData();
  }

  if (view === 'tier-painter' && session.authenticated) {
    return <TierPainterView token={session.token} onBack={() => setView('customer')} />;
  }

  return (
    <main style={{ maxWidth: 900, margin: '0 auto', padding: '1.25rem', width: '100%' }}>
      <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <button type="button" onClick={() => setView('home')}>Home</button>

        <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
          <button type="button" onClick={openShop}>Shop</button>
          {session.authenticated ? (
            <>
              <button type="button" onClick={() => setView('dashboard')}>Dashboard</button>
              <button type="button" onClick={openCustomer}>Customer</button>
              <button type="button" onClick={signOut}>Log Out</button>
            </>
          ) : (
            <>
              <button type="button" onClick={openAuth}>Sign In</button>
            </>
          )}
        </div>
      </header>

      {/*
        <section style={{ marginTop: '1rem' }}>
        <strong>Status:</strong> <span>{status}</span>
      </section>
      */}

      {view === 'home' && <HomeView />}
      {view === 'auth' && !session.authenticated && (
        <AuthView loading={loading} onStartSteamSignIn={startSteamSignIn} />
      )}
      {view === 'dashboard' && session.authenticated && (
        <DashboardView
          currentUser={session.currentUser}
          sessionId={session.sessionId}
          premiumUser={session.premiumUser}
          subscriptionStatus={session.subscriptionStatus}
          accountSetupComplete={session.accountSetupComplete}
          emailVerified={session.emailVerified}
          steam64Id={session.steam64Id}
          discordUserId={session.discordUserId}
          discordUsername={session.discordUsername}
          loading={loading}
          authenticated={session.authenticated}
          protectedData={protectedData}
          onStartDiscordLink={startDiscordLink}
          onProtectedHello={() => handleProtectedRequest('/hello')}
          onProtectedTest={() => handleProtectedRequest('/test')}
        />
      )}
      {view === 'customer' && session.authenticated && (
        <CustomerView
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
          premiumUser={session.premiumUser}
          subscriptionStatus={session.subscriptionStatus}
          subscriptionId={session.subscriptionId}
          subscriptionCancelAtPeriodEnd={session.subscriptionCancelAtPeriodEnd}
          subscriptionCurrentPeriodEnd={session.subscriptionCurrentPeriodEnd}
          subscriptionCancelAt={session.subscriptionCancelAt}
          shopLoading={shopLoading}
          products={products}
          orders={orders}
          onStartSubscription={handleStartSubscription}
          onCancelSubscription={handleCancelSubscription}
          onBuy={handleBuy}
          onDownload={handleDownload}
        />
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

      <footer style={{ marginTop: '2rem', paddingTop: '0.75rem', borderTop: '1px solid #ddd', display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
        <button type="button" onClick={() => openPolicy('terms')}>Terms</button>
        <button type="button" onClick={() => openPolicy('privacy')}>Privacy</button>
        <button type="button" onClick={() => openPolicy('refund')}>Refund</button>
      </footer>
    </main>
  );
}
