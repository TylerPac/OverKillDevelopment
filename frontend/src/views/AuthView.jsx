export default function AuthView({ loading, onStartSteamSignIn }) {
  return (
    <section style={{ marginTop: '2rem', maxWidth: 440 }}>
      <h2 style={{ marginBottom: '0.25rem' }}>Sign In</h2>
      <p style={{ marginTop: 0, opacity: 0.85 }}>
        Use Steam OpenID to sign in. After sign-in, you can optionally link Discord from your dashboard.
      </p>
      <button type="button" disabled={loading} onClick={onStartSteamSignIn}>
        Continue With Steam
      </button>
    </section>
  );
}
