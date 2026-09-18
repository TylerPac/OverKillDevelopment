export default function AuthView({ loading, status, onStartSteamSignIn }) {
  return (
    <section style={{
      maxWidth: 420,
      background: '#1e1e2e',
      border: '1px solid #333',
      borderRadius: 8,
      padding: '1.5rem',
      marginTop: '2rem',
    }}>
      <h2>Sign In</h2>
      <p style={{ color: '#aaa', marginBottom: '1.25rem' }}>
        Use Steam OpenID to sign in. After sign-in you can optionally link Discord from your dashboard.
      </p>
      <button
        type="button"
        disabled={loading}
        onClick={onStartSteamSignIn}
        style={{ background: '#3a3a5e', padding: '0.5em 1.2em', fontSize: '0.9rem' }}
      >
        Continue with Steam
      </button>
      {status && (
        <p style={{ color: '#ccd', marginTop: '1rem', fontSize: '0.85rem' }}>{status}</p>
      )}
    </section>
  );
}
