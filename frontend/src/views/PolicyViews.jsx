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
      <p style={leadStyle}>
        All sales are final. We do not offer refunds on any purchases.
      </p>
      <p>
        All products sold by OverKill Development are digital goods. Upon purchase you are granted immediate access to the source code and associated files. Because the product is delivered digitally and cannot be returned, all transactions are non-refundable once completed.
      </p>
      <p>
        Please review all product descriptions and preview materials carefully before purchasing. If you have questions about a product prior to purchase, contact us through our Discord community.
      </p>
      <p>
        In the event of a technical delivery failure (e.g. you did not receive access after a successful payment), please contact us and we will resolve the issue promptly. This does not constitute a refund — it is a delivery correction.
      </p>
    </section>
  );
}
