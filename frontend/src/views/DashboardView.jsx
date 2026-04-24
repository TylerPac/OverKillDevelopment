export default function DashboardView({
  currentUser,
  sessionId,
  premiumUser,
  subscriptionStatus,
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
    <section style={{ marginTop: '2rem' }}>
      {/*
      <h2 style={{ marginBottom: '0.25rem' }}>Customer Dashboard</h2>
      */}
      <p style={{ marginTop: 0 }}>Signed in as <strong>{currentUser || 'user'}</strong></p>
      
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
      <p style={{ marginTop: 0 }}>
        Discord: <strong>{discordUserId ? `${discordUsername}` : 'not linked'}</strong>
        {
        /*
        Discord: <strong>{discordUserId ? `${discordUsername} (${discordUserId})` : 'not linked'}</strong>
        */
        }
      </p>

      <div style={{ marginTop: '1rem', display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
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
