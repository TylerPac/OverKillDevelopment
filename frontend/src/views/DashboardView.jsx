export default function DashboardView({
  currentUser,
  sessionId,
  accountSetupComplete,
  emailVerified,
  steam64Id,
  discordUserId,
  discordUsername,
  loading,
  authenticated,
  protectedData,
  onStartDiscordLink,
  onProtectedHello,
  onProtectedTest,
}) {
  return (
    <section style={{ marginTop: '1rem' }}>
      <h2>Dashboard</h2>
      <p style={{ color: '#aaa', marginBottom: '1rem' }}>Signed in as <strong style={{ color: '#eee' }}>{currentUser || 'user'}</strong></p>
      
      {/*
      <p style={{ marginTop: 0 }}>
        Session ID: <strong>{sessionId || 'n/a'}</strong>
      </p>
      <p style={{ marginTop: 0 }}>
        Membership: <strong>{premiumUser ? 'Premium' : 'Standard'}</strong>
        {' '}({String(subscriptionStatus || 'none')})
      </p>
      <p style={{ marginTop: 0 }}>
        Account Setup: <strong>{accountSetupComplete ? 'Complete' : 'Not setup'}</strong>
        {!emailVerified && ' (additional verification required)'}
      </p>
      
      <p style={{ marginTop: 0 }}>
        Steam64 ID: <strong>{steam64Id || 'not linked'}</strong>
      </p>
      */}
      <p style={{ color: '#aaa', marginBottom: '1rem' }}>
        Discord: <strong style={{ color: '#eee' }}>{discordUserId ? discordUsername : 'not linked'}</strong>
        {
        /*
        Discord: <strong>{discordUserId ? `${discordUsername} (${discordUserId})` : 'not linked'}</strong>
        */
        }
      </p>

      <div style={{ marginTop: '0.5rem', display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
        <button type="button" disabled={loading || !authenticated} onClick={onStartDiscordLink}>
          {discordUserId ? 'Re-link Discord' : 'Link Discord'}
        </button>
        {/*<button type="button" disabled={loading || !authenticated} onClick={onProtectedHello}>
          Load /hello
        </button>
        <button type="button" disabled={loading || !authenticated} onClick={onProtectedTest}>
          Load /test
        </button>
        */}
      </div>

      {/*
      <div style={{ marginTop: '1rem' }}>
        <strong>API Response:</strong>
        <pre style={{ whiteSpace: 'pre-wrap' }}>{protectedData}</pre>
      </div>
      */}
    </section>
  );
}
