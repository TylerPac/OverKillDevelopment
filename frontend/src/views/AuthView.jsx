export default function AuthView({ loading, status, onStartSteamSignIn }) {
  return (
    <section className="panel panel-accent narrow">
      <p className="kicker"><span className="tag">Access</span> Steam OpenID</p>
      <h1>Sign In</h1>
      <p className="muted">
        Use Steam OpenID to sign in. After sign-in you can optionally link Discord from your dashboard.
      </p>
      <button
        type="button"
        disabled={loading}
        onClick={onStartSteamSignIn}
        className="btn btn-primary btn-lg btn-block"
      >
        Continue with Steam
      </button>
      {status && (
        <p className="notice mt-4">{status}</p>
      )}
    </section>
  );
}
