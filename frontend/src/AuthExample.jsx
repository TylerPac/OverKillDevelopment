import { useState } from 'react';

const baseUrl = (typeof import.meta !== 'undefined' && import.meta.env && import.meta.env.VITE_API_URL) || 'http://localhost:8080';

export default function AuthExample() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [message, setMessage] = useState('');

  async function post(path, body) {
    const res = await fetch(`${baseUrl}${path}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    return res.json();
  }

  async function handleRegister(e) {
    e.preventDefault();
    try {
      const data = await post('/auth/register', { username, password });
      if (data.token) {
        localStorage.setItem('token', data.token);
        setMessage('Registered and logged in. Token saved.');
      } else setMessage(JSON.stringify(data));
    } catch (err) { setMessage(String(err)); }
  }

  async function handleLogin(e) {
    e.preventDefault();
    try {
      const data = await post('/auth/login', { username, password });
      if (data.token) {
        localStorage.setItem('token', data.token);
        setMessage('Logged in. Token saved.');
      } else setMessage(JSON.stringify(data));
    } catch (err) { setMessage(String(err)); }
  }

  return (
    <div style={{maxWidth:400}}>
      <h3>Auth Example</h3>
      <form onSubmit={handleLogin}>
        <div>
          <label>Username</label>
          <input value={username} onChange={e=>setUsername(e.target.value)} />
        </div>
        <div>
          <label>Password</label>
          <input type="password" value={password} onChange={e=>setPassword(e.target.value)} />
        </div>
        <div style={{marginTop:8}}>
          <button onClick={handleRegister}>Register</button>
          <button type="submit" style={{marginLeft:8}}>Login</button>
        </div>
      </form>
      <div style={{marginTop:12}}><strong>Status:</strong> {message}</div>
    </div>
  );
}
