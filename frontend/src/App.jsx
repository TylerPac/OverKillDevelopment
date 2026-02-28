import { useEffect, useMemo, useState } from 'react';

const apiBase =
  (typeof import.meta !== 'undefined' && import.meta.env && import.meta.env.VITE_API_URL) ||
  'http://localhost:8081';

const TOKEN_KEY = 'overkilldevelopment_jwt_token';
const USER_KEY = 'overkilldevelopment_username';

function parseJwt(token) {
  try {
    const payload = token.split('.')[1];
    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
    const decoded = atob(normalized);
    return JSON.parse(decoded);
  } catch {
    return null;
  }
}

function isTokenValid(token) {
  if (!token) return false;
  const payload = parseJwt(token);
  if (!payload || !payload.exp) return false;
  return payload.exp * 1000 > Date.now();
}

async function authRequest(path, username, password, email, options = {}) {
  const { expectToken = true } = options;
  const payload = { username, password };
  if (email !== undefined) {
    payload.email = email;
  }

  const response = await fetch(`${apiBase}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });

  const contentType = response.headers.get('content-type') || '';
  const body = contentType.includes('application/json') ? await response.json() : await response.text();

  if (!response.ok) {
    const message = typeof body === 'string' ? body : body?.message || 'Authentication failed';
    throw new Error(message);
  }

  if (!expectToken) {
    return body;
  }

  if (!body?.token) {
    throw new Error('No JWT token was returned by the backend');
  }

  return body.token;
}

async function callProtected(path, token) {
  const response = await fetch(`${apiBase}${path}`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });

  const text = await response.text();
  if (!response.ok) {
    throw new Error(text || `Request failed (${response.status})`);
  }

  return text;
}

async function callJson(path, options = {}) {
  const response = await fetch(`${apiBase}${path}`, options);
  const contentType = response.headers.get('content-type') || '';
  const body = contentType.includes('application/json') ? await response.json() : await response.text();

  if (!response.ok) {
    const message = typeof body === 'string' ? body : body?.message || `Request failed (${response.status})`;
    throw new Error(message);
  }

  return body;
}

function getSessionId(token) {
  if (!token) return '';
  const parts = token.split('.');
  if (parts.length < 3) return token.slice(0, 12);
  return parts[2].slice(0, 12);
}

export default function App() {
  const [view, setView] = useState('home');
  const [mode, setMode] = useState('login');
  const [awaitingEmailVerification, setAwaitingEmailVerification] = useState(false);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [email, setEmail] = useState('');
  const [token, setToken] = useState(() => localStorage.getItem(TOKEN_KEY) || '');
  const [currentUser, setCurrentUser] = useState(() => localStorage.getItem(USER_KEY) || '');
  const [status, setStatus] = useState('Enter a username and password to sign up or log in.');
  const [protectedData, setProtectedData] = useState('No protected request made yet.');
  const [loading, setLoading] = useState(false);
  const [shopLoading, setShopLoading] = useState(false);
  const [products, setProducts] = useState([]);
  const [orders, setOrders] = useState([]);

  const authenticated = useMemo(() => isTokenValid(token), [token]);
  const sessionId = useMemo(() => getSessionId(token), [token]);

  const emailLooksValid = useMemo(() => {
    if (!email) return false;
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
  }, [email]);

  const paidOrders = useMemo(
    () => orders.filter((order) => String(order.status || '').toUpperCase() === 'PAID'),
    [orders],
  );

  useEffect(() => {
    if (token && !isTokenValid(token)) {
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(USER_KEY);
      setToken('');
      setCurrentUser('');
      setView('auth');
      setStatus('Session expired. Please log in again.');
    }
  }, [token]);

  useEffect(() => {
    const path = window.location.pathname;
    if (path !== '/verify-email') {
      return;
    }

    const tokenFromUrl = new URLSearchParams(window.location.search).get('token');
    setView('auth');
    setMode('login');
    setAwaitingEmailVerification(false);

    if (!tokenFromUrl) {
      setStatus('Verification failed: missing token in the link.');
      window.history.replaceState({}, '', '/');
      return;
    }

    async function verifyEmailFromLink() {
      setLoading(true);
      setStatus('Verifying your email...');

      try {
        const response = await fetch(
          `${apiBase}/auth/verify-email?token=${encodeURIComponent(tokenFromUrl)}`,
          { method: 'GET' },
        );
        const text = await response.text();

        if (!response.ok) {
          throw new Error(text || 'Verification failed');
        }

        setStatus('Email verified successfully. You can log in now.');
      } catch (error) {
        setStatus(`Verification failed: ${error.message}`);
      } finally {
        setLoading(false);
        window.history.replaceState({}, '', '/');
      }
    }

    verifyEmailFromLink();
  }, []);

  useEffect(() => {
    const search = new URLSearchParams(window.location.search);
    const checkout = search.get('checkout');
    if (!checkout) {
      return;
    }

    setView('shop');
    if (checkout === 'success') {
      setStatus('Payment completed. Stripe webhook will update your order status shortly.');
    } else if (checkout === 'cancel') {
      setStatus('Checkout canceled. No charge was made.');
    }

    window.history.replaceState({}, '', '/');
  }, []);

  useEffect(() => {
    const onStorage = () => {
      setToken(localStorage.getItem(TOKEN_KEY) || '');
      setCurrentUser(localStorage.getItem(USER_KEY) || '');
    };

    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, []);

  function saveSession(nextToken, nextUser) {
    localStorage.setItem(TOKEN_KEY, nextToken);
    localStorage.setItem(USER_KEY, nextUser);
    setToken(nextToken);
    setCurrentUser(nextUser);
    setView('dashboard');
  }

  function clearSession(message) {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    setToken('');
    setCurrentUser('');
    setProtectedData('No protected request made yet.');
    setView('home');
    setStatus(message);
    setOrders([]);
  }

  async function loadShopData() {
    setShopLoading(true);
    try {
      const nextProducts = await callJson('/shop/products');
      setProducts(Array.isArray(nextProducts) ? nextProducts : []);

      if (!authenticated || !token) {
        setOrders([]);
        return;
      }

      const nextOrders = await callJson('/shop/orders', {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      });
      setOrders(Array.isArray(nextOrders) ? nextOrders : []);
    } catch (error) {
      setStatus(`Shop load failed: ${error.message}`);
    } finally {
      setShopLoading(false);
    }
  }

  async function loadCustomerData() {
    if (!authenticated || !token) {
      setOrders([]);
      return;
    }

    setShopLoading(true);
    try {
      const nextOrders = await callJson('/shop/orders', {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      });
      setOrders(Array.isArray(nextOrders) ? nextOrders : []);
    } catch (error) {
      setStatus(`Customer page load failed: ${error.message}`);
    } finally {
      setShopLoading(false);
    }
  }

  async function handleBuy(productId) {
    if (!authenticated || !token) {
      setStatus('Please log in before purchasing.');
      setView('auth');
      setMode('login');
      return;
    }

    setShopLoading(true);
    setStatus('Creating Stripe checkout session...');

    try {
      const idempotencyKey = `checkout-${productId}-${Date.now()}`;
      const response = await callJson('/shop/checkout-session', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
          'Idempotency-Key': idempotencyKey,
        },
        body: JSON.stringify({ productId }),
      });

      if (!response?.checkoutUrl) {
        throw new Error('No checkout URL returned by backend');
      }

      window.location.assign(response.checkoutUrl);
    } catch (error) {
      setStatus(`Checkout failed: ${error.message}`);
      setShopLoading(false);
    }
  }

  async function handleDownload(productId) {
    if (!authenticated || !token) {
      setStatus('Please log in before downloading.');
      setView('auth');
      return;
    }

    setShopLoading(true);
    setStatus('Preparing your download...');

    try {
      const response = await fetch(`${apiBase}/shop/download/${encodeURIComponent(productId)}`, {
        method: 'GET',
        headers: {
          Authorization: `Bearer ${token}`,
        },
      });

      if (!response.ok) {
        const message = await response.text();
        throw new Error(message || `Download failed (${response.status})`);
      }

      const blob = await response.blob();
      const disposition = response.headers.get('content-disposition') || '';
      const fileNameMatch = disposition.match(/filename="?([^";]+)"?/i);
      const fileName = fileNameMatch ? fileNameMatch[1] : `${productId}.zip`;

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
      } else if (error.message === 'email_not_verified') {
        setStatus('Download failed: verify your email first.');
      } else if (error.message === 'download_not_found') {
        setStatus('Download failed: file is not available yet.');
      } else {
        setStatus(`Download failed: ${error.message}`);
      }
    } finally {
      setShopLoading(false);
    }
  }

  async function handleRegister(event) {
    event.preventDefault();

    if (!emailLooksValid) {
      setStatus('Register failed: please enter a valid email address.');
      return;
    }

    setLoading(true);
    setStatus('Creating account and sending verification email...');

    try {
      await authRequest('/auth/register', username, password, email, { expectToken: false });
      setPassword('');
      setAwaitingEmailVerification(true);
      setStatus('Account created. Please verify your account, then log in.');
    } catch (error) {
      setStatus(`Register failed: ${error.message}`);
    } finally {
      setLoading(false);
    }
  }

  async function handleLogin(event) {
    event.preventDefault();
    setLoading(true);
    setStatus('Signing in...');

    try {
      const nextToken = await authRequest('/auth/login', username, password);
      saveSession(nextToken, username);
      setStatus(`Signed in as ${username}.`);
    } catch (error) {
      if (error.message === 'email_not_verified') {
        setStatus('Login failed: verify your email first, then try again.');
      } else {
        setStatus(`Login failed: ${error.message}`);
      }
    } finally {
      setLoading(false);
    }
  }

  async function handleProtectedRequest(path) {
    if (!authenticated) {
      setStatus('Please log in first.');
      return;
    }

    setLoading(true);
    setStatus(`Calling ${path} with JWT...`);

    try {
      const data = await callProtected(path, token);
      setProtectedData(data);
      setStatus(`Protected request to ${path} succeeded.`);
    } catch (error) {
      if (String(error.message).includes('401')) {
        clearSession('Session is invalid or expired. Please sign in again.');
      } else {
        setStatus(`Protected request failed: ${error.message}`);
      }
    } finally {
      setLoading(false);
    }
  }

  function openAuth(nextMode) {
    setMode(nextMode);
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
    if (!authenticated || !token) {
      setStatus('Please log in to access customer downloads.');
      setView('auth');
      setMode('login');
      return;
    }

    setView('customer');
    loadCustomerData();
  }

  function renderHome() {
    return (
      <section style={{ marginTop: '2.5rem' }}>
        <h1 style={{ marginBottom: '0.5rem' }}>Welcome to OverKill Development</h1>
        <p style={{ marginTop: 0, opacity: 0.85 }}>
          Home page for visitors. Use the top-right actions to sign up or log in.
        </p>
      </section>
    );
  }

  function renderAuth() {
    if (awaitingEmailVerification) {
      return (
        <section style={{ marginTop: '2rem', maxWidth: 440 }}>
          <h2 style={{ marginBottom: '0.25rem' }}>Verify Your Account</h2>
          <p style={{ marginTop: 0, opacity: 0.85 }}>
            Please verify your account and then log in.
          </p>
        </section>
      );
    }

    return (
      <section style={{ marginTop: '2rem', maxWidth: 440 }}>
        <h2 style={{ marginBottom: '0.25rem' }}>{mode === 'signup' ? 'Create Account' : 'Login'}</h2>
        <p style={{ marginTop: 0, opacity: 0.85 }}>
          {mode === 'signup'
            ? 'Create your user, verify your email, then log in to reach your dashboard.'
            : 'Sign in with your account to access your dashboard.'}
        </p>

        <form style={{ display: 'grid', gap: '0.75rem' }} onSubmit={mode === 'signup' ? handleRegister : handleLogin}>
          {mode === 'signup' && (
            <>
              <label htmlFor="email">Email</label>
              <input
                id="email"
                type="email"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                autoComplete="email"
                required
              />
            </>
          )}

          <label htmlFor="username">Username</label>
          <input
            id="username"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            autoComplete="username"
            minLength={3}
            required
          />

          <label htmlFor="password">Password</label>
          <input
            id="password"
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete={mode === 'signup' ? 'new-password' : 'current-password'}
            minLength={6}
            required
          />

          <button
            type="submit"
            disabled={loading || !username || !password || (mode === 'signup' && !emailLooksValid)}
          >
            {mode === 'signup' ? 'Sign Up' : 'Log In'}
          </button>
        </form>

        <div style={{ marginTop: '0.75rem', display: 'flex', gap: '0.75rem' }}>
          <button type="button" onClick={() => setMode('login')} disabled={loading || mode === 'login'}>
            Switch to Login
          </button>
          <button type="button" onClick={() => setMode('signup')} disabled={loading || mode === 'signup'}>
            Switch to Sign Up
          </button>
        </div>
      </section>
    );
  }

  function renderDashboard() {
    return (
      <section style={{ marginTop: '2rem' }}>
        <h2 style={{ marginBottom: '0.25rem' }}>Customer Dashboard</h2>
        <p style={{ marginTop: 0 }}>Signed in as <strong>{currentUser || 'user'}</strong></p>
        <p style={{ marginTop: 0 }}>
          Session ID: <strong>{sessionId || 'n/a'}</strong>
        </p>

        <div style={{ marginTop: '1rem', display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
          <button type="button" disabled={loading || !authenticated} onClick={() => handleProtectedRequest('/hello')}>
            Load /hello
          </button>
          <button type="button" disabled={loading || !authenticated} onClick={() => handleProtectedRequest('/test')}>
            Load /test
          </button>
        </div>

        <div style={{ marginTop: '1rem' }}>
          <strong>API Response:</strong>
          <pre style={{ whiteSpace: 'pre-wrap' }}>{protectedData}</pre>
        </div>
      </section>
    );
  }

  function renderShop() {
    return (
      <section style={{ marginTop: '2rem' }}>
        <h2 style={{ marginBottom: '0.25rem' }}>Shop</h2>
        <p style={{ marginTop: 0, opacity: 0.85 }}>
          Buy a package through Stripe Checkout. Orders are saved to your profile.
        </p>

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
                disabled={shopLoading}
                onClick={() => handleBuy(product.id)}
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
            {!orders.length && <p>No orders yet.</p>}
            {orders.map((order) => (
              <div key={order.id} style={{ marginBottom: '0.6rem' }}>
                #{order.id} · {order.productName} · {order.status} · {(order.amountCents / 100).toFixed(2)}{' '}
                {String(order.currency || '').toUpperCase()}
                {order.status === 'PAID' && (
                  <button
                    type="button"
                    style={{ marginLeft: '0.6rem' }}
                    disabled={shopLoading}
                    onClick={() => handleDownload(order.productId)}
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

  function renderCustomer() {
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
                onClick={() => handleDownload(order.productId)}
              >
                Download
              </button>
            </article>
          ))}
        </div>
      </section>
    );
  }

  function renderTerms() {
    return (
      <section style={{ marginTop: '2rem', maxWidth: 860 }}>
        <h2 style={{ marginBottom: '0.5rem' }}>Terms of Service</h2>
        <p>By purchasing from OverKill Development, you agree to these terms.</p>
        <ul>
          <li>Digital products are licensed, not sold.</li>
          <li>Do not redistribute or resell purchased assets.</li>
          <li>Account access may be suspended for abuse or fraud.</li>
          <li>Prices, availability, and product details can change.</li>
        </ul>
      </section>
    );
  }

  function renderPrivacy() {
    return (
      <section style={{ marginTop: '2rem', maxWidth: 860 }}>
        <h2 style={{ marginBottom: '0.5rem' }}>Privacy Policy</h2>
        <p>OverKill Development collects only the data needed to provide accounts and purchases.</p>
        <ul>
          <li>We store account data (username, email, encrypted password).</li>
          <li>Payment card data is handled by Stripe and never stored by OverKill Development.</li>
          <li>Order, billing, and security logs are retained for fraud prevention and support.</li>
          <li>You can request account deletion and data export by contacting support.</li>
        </ul>
      </section>
    );
  }

  function renderRefund() {
    return (
      <section style={{ marginTop: '2rem', maxWidth: 860 }}>
        <h2 style={{ marginBottom: '0.5rem' }}>Refund Policy</h2>
        <p>For digital products, refunds are handled under the rules below.</p>
        <ul>
          <li>Refund requests are accepted within 14 days of purchase.</li>
          <li>Refunds are available for duplicate purchases or technical delivery failures.</li>
          <li>No refunds for policy violations, abuse, or completed custom work.</li>
          <li>Approved refunds are returned to the original payment method via Stripe.</li>
        </ul>
      </section>
    );
  }

  return (
    <main style={{ maxWidth: 900, margin: '0 auto', padding: '1.25rem', width: '100%' }}>
      <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <button type="button" onClick={() => setView('home')}>Home</button>

        <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
          <button type="button" onClick={openShop}>Shop</button>
          {authenticated ? (
            <>
              <button type="button" onClick={() => setView('dashboard')}>Dashboard</button>
              <button type="button" onClick={openCustomer}>Customer</button>
              <button type="button" onClick={() => clearSession('Signed out.')}>Log Out</button>
            </>
          ) : (
            <>
              <button type="button" onClick={() => openAuth('login')}>Login</button>
              <button type="button" onClick={() => openAuth('signup')}>Sign Up</button>
            </>
          )}
        </div>
      </header>

      <section style={{ marginTop: '1rem' }}>
        <strong>Status:</strong> <span>{status}</span>
      </section>

      {view === 'home' && renderHome()}
      {view === 'auth' && !authenticated && renderAuth()}
      {view === 'dashboard' && authenticated && renderDashboard()}
      {view === 'customer' && authenticated && renderCustomer()}
      {view === 'shop' && renderShop()}
      {view === 'terms' && renderTerms()}
      {view === 'privacy' && renderPrivacy()}
      {view === 'refund' && renderRefund()}

      {view === 'dashboard' && !authenticated && (
        <section style={{ marginTop: '1.5rem' }}>
          <p>Please log in to access the dashboard.</p>
          <button type="button" onClick={() => openAuth('login')}>Go to Login</button>
        </section>
      )}

      {view === 'customer' && !authenticated && (
        <section style={{ marginTop: '1.5rem' }}>
          <p>Please log in to access customer downloads.</p>
          <button type="button" onClick={() => openAuth('login')}>Go to Login</button>
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
