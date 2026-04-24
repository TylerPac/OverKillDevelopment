import { useState } from 'react';

const baseUrl =
  (typeof import.meta !== 'undefined' && import.meta.env && import.meta.env.VITE_API_BASE_URL) ||
  'http://localhost:8080';

export default function AuthExample() {
  const [message, setMessage] = useState('');

  async function get(path) {
    const res = await fetch(`${baseUrl}${path}`, {
      method: 'GET',
    });

    const contentType = res.headers.get('content-type') || '';
    const body = contentType.includes('application/json') ? await res.json() : await res.text();
    if (!res.ok) {
      const messageText = typeof body === 'string' ? body : body?.message || 'Request failed';
      throw new Error(messageText);
    }

    return body;
  }

  async function handleSteamSignIn(e) {
    e.preventDefault();
    try {
      const data = await get('/auth/steam/login-url');
      if (!data?.url) {
        throw new Error('Steam login URL was not returned by backend');
      }
      window.location.assign(data.url);
    } catch (err) {
      setMessage(String(err));
    }
  }

  return (
    <div style={{maxWidth:400}}>
      <h3>Auth Example</h3>
      <form onSubmit={handleSteamSignIn}>
        <p>Start the Steam OpenID sign-in flow.</p>
        <div style={{marginTop:8}}>
          <button type="submit">Continue with Steam</button>
        </div>
      </form>
      <div style={{marginTop:12}}><strong>Status:</strong> {message}</div>
    </div>
  );
}
