const sectionStyle = { marginTop: '1rem', maxWidth: 720 };
const leadStyle = { color: '#aaa', marginBottom: '0.75rem' };

export function TermsView() {
  return (
    <section style={sectionStyle}>
      <h2>Terms of Service</h2>
      <p style={leadStyle}>By purchasing from OverKill Development, you agree to these terms.</p>
      <ul>
        <li>Digital products are licensed, not sold.</li>
        <li>Do not redistribute or resell purchased assets.</li>
        <li>Account access may be suspended for abuse or fraud.</li>
        <li>Prices, availability, and product details can change.</li>
      </ul>
    </section>
  );
}

export function PrivacyView() {
  return (
    <section style={sectionStyle}>
      <h2>Privacy Policy</h2>
      <p style={leadStyle}>OverKill Development collects only the data needed to provide accounts and purchases.</p>
      <ul>
        <li>We store account data (Steam64 ID, username, optional Discord identifiers).</li>
        <li>Payment card data is handled by Stripe and never stored by OverKill Development.</li>
        <li>Order, billing, and security logs are retained for fraud prevention and support.</li>
        <li>You can request account deletion and data export by contacting support.</li>
      </ul>
    </section>
  );
}

export function RefundView() {
  return (
    <section style={sectionStyle}>
      <h2>Refund Policy</h2>
      <p style={leadStyle}>For digital products, refunds are handled under the rules below.</p>
      <ul>
        <li>Refund requests are accepted within 14 days of purchase.</li>
        <li>Refunds are available for duplicate purchases or technical delivery failures.</li>
        <li>No refunds for policy violations, abuse, or completed custom work.</li>
        <li>Approved refunds are returned to the original payment method via Stripe.</li>
      </ul>
    </section>
  );
}
