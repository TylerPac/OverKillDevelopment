const fs = require('fs');
const crypto = require('crypto');

const username = process.argv[2] || '76561199155324762';
const envPath = 'backend/.env.development';
let secret = null;
try {
  const env = fs.readFileSync(envPath, 'utf8');
  const m = env.match(/^JWT_SECRET=(.*)$/m);
  if (m) secret = m[1].trim();
} catch (e) {
  // ignore
}
if (!secret) {
  // fallback to env var
  secret = process.env.JWT_SECRET;
}
if (!secret) {
  console.error('JWT secret not found in backend/.env.development or environment');
  process.exit(2);
}

function base64url(buf) {
  return Buffer.from(buf).toString('base64')
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
}

const header = { alg: 'HS256', typ: 'JWT' };
const now = Math.floor(Date.now() / 1000);
const payload = { sub: username, iat: now, exp: now + 3600 };
const encodedHeader = base64url(JSON.stringify(header));
const encodedPayload = base64url(JSON.stringify(payload));
const signingInput = encodedHeader + '.' + encodedPayload;
const sig = crypto.createHmac('sha256', secret).update(signingInput).digest();
const encodedSig = base64url(sig);
console.log(signingInput + '.' + encodedSig);
