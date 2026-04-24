import { callJson } from './apiClient';

export function getProducts() {
  return callJson('/shop/products');
}

export function getOrders(token) {
  return callJson('/shop/orders', {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

export function getSubscriptionStatus(token) {
  return callJson('/shop/subscription-status', {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

export function createCheckoutSession(productId, token, idempotencyKey) {
  return callJson('/shop/checkout-session', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
      'Idempotency-Key': idempotencyKey,
    },
    body: JSON.stringify({ productId }),
  });
}

export function createCartCheckoutSession(productIds, token, idempotencyKey) {
  return callJson('/shop/cart-checkout-session', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
      'Idempotency-Key': idempotencyKey,
    },
    body: JSON.stringify({ productIds }),
  });
}

export function createSubscriptionCheckoutSession(token, idempotencyKey) {
  return callJson('/shop/subscription/checkout-session', {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      'Idempotency-Key': idempotencyKey,
    },
  });
}

export function syncSubscriptionFromSession(token, sessionId) {
  return callJson('/shop/subscription/sync', {
    method: 'POST',
    headers: {
      'Content-Type': 'text/plain',
      Authorization: `Bearer ${token}`,
    },
    body: sessionId,
  });
}

export function cancelSubscription(token) {
  return callJson('/shop/subscription/cancel', {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}
