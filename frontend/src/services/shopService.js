import { callJson } from './apiClient';

export function getProducts() {
  return callJson('/shop/products');
}

export function getOrders() {
  return callJson('/shop/orders');
}

export function getSubscriptionStatus() {
  return callJson('/shop/subscription-status');
}

export function createCheckoutSession(productId, idempotencyKey) {
  return callJson('/shop/checkout-session', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': idempotencyKey,
    },
    body: JSON.stringify({ productId }),
  });
}

export function createCartCheckoutSession(productIds, idempotencyKey) {
  return callJson('/shop/cart-checkout-session', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': idempotencyKey,
    },
    body: JSON.stringify({ productIds }),
  });
}

export function syncCheckoutFromSession(sessionId) {
  return callJson('/shop/checkout/sync', {
    method: 'POST',
    headers: {
      'Content-Type': 'text/plain',
    },
    body: sessionId,
  });
}

export function createSubscriptionCheckoutSession(idempotencyKey) {
  return callJson('/shop/subscription/checkout-session', {
    method: 'POST',
    headers: {
      'Idempotency-Key': idempotencyKey,
    },
  });
}

export function syncSubscriptionFromSession(sessionId) {
  return callJson('/shop/subscription/sync', {
    method: 'POST',
    headers: {
      'Content-Type': 'text/plain',
    },
    body: sessionId,
  });
}

export function cancelSubscription() {
  return callJson('/shop/subscription/cancel', {
    method: 'POST',
  });
}
