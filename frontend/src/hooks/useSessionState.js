import { useEffect, useMemo, useState } from 'react';
import {
  ACCOUNT_SETUP_KEY,
  DISCORD_USER_ID_KEY,
  DISCORD_USERNAME_KEY,
  EMAIL_VERIFIED_KEY,
  PREMIUM_KEY,
  REFRESH_TOKEN_KEY,
  STEAM64_KEY,
  SUBSCRIPTION_CANCEL_AT_KEY,
  SUBSCRIPTION_CANCEL_AT_PERIOD_END_KEY,
  SUBSCRIPTION_CURRENT_PERIOD_END_KEY,
  SUBSCRIPTION_ID_KEY,
  SUBSCRIPTION_STATUS_KEY,
  TOKEN_KEY,
  USER_KEY,
} from '../constants/storageKeys';
import { getAuthProfile } from '../services/authService';
import { getSessionId, isTokenValid } from '../utils/authToken';

export default function useSessionState(onSessionExpired) {
  const [token, setToken] = useState(() => localStorage.getItem(TOKEN_KEY) || '');
  const [currentUser, setCurrentUser] = useState(() => localStorage.getItem(USER_KEY) || '');
  const [steam64Id, setSteam64Id] = useState(() => localStorage.getItem(STEAM64_KEY) || '');
  const [discordUserId, setDiscordUserId] = useState(() => localStorage.getItem(DISCORD_USER_ID_KEY) || '');
  const [discordUsername, setDiscordUsername] = useState(() => localStorage.getItem(DISCORD_USERNAME_KEY) || '');
  const [premiumUser, setPremiumUser] = useState(() => localStorage.getItem(PREMIUM_KEY) === 'true');
  const [subscriptionStatus, setSubscriptionStatus] = useState(() => localStorage.getItem(SUBSCRIPTION_STATUS_KEY) || 'none');
  const [subscriptionId, setSubscriptionId] = useState(() => localStorage.getItem(SUBSCRIPTION_ID_KEY) || '');
  const [subscriptionCancelAtPeriodEnd, setSubscriptionCancelAtPeriodEnd] = useState(
    () => localStorage.getItem(SUBSCRIPTION_CANCEL_AT_PERIOD_END_KEY) === 'true',
  );
  const [subscriptionCancelAt, setSubscriptionCancelAt] = useState(() => localStorage.getItem(SUBSCRIPTION_CANCEL_AT_KEY) || '');
  const [subscriptionCurrentPeriodEnd, setSubscriptionCurrentPeriodEnd] = useState(
    () => localStorage.getItem(SUBSCRIPTION_CURRENT_PERIOD_END_KEY) || '',
  );
  const [accountSetupComplete, setAccountSetupComplete] = useState(() => localStorage.getItem(ACCOUNT_SETUP_KEY) === 'true');
  const [emailVerified, setEmailVerified] = useState(() => localStorage.getItem(EMAIL_VERIFIED_KEY) === 'true');

  const authenticated = useMemo(() => isTokenValid(token), [token]);
  const sessionId = useMemo(() => getSessionId(token), [token]);

  useEffect(() => {
    if (token && !isTokenValid(token)) {
      clearSession();
      if (onSessionExpired) {
        onSessionExpired('Session expired. Please sign in with Steam again.');
      }
    }
  }, [token, onSessionExpired]);

  useEffect(() => {
    const onStorage = () => {
      setToken(localStorage.getItem(TOKEN_KEY) || '');
      setCurrentUser(localStorage.getItem(USER_KEY) || '');
      setPremiumUser(localStorage.getItem(PREMIUM_KEY) === 'true');
      setSubscriptionStatus(localStorage.getItem(SUBSCRIPTION_STATUS_KEY) || 'none');
      setSubscriptionId(localStorage.getItem(SUBSCRIPTION_ID_KEY) || '');
      setSubscriptionCancelAtPeriodEnd(localStorage.getItem(SUBSCRIPTION_CANCEL_AT_PERIOD_END_KEY) === 'true');
      setSubscriptionCancelAt(localStorage.getItem(SUBSCRIPTION_CANCEL_AT_KEY) || '');
      setSubscriptionCurrentPeriodEnd(localStorage.getItem(SUBSCRIPTION_CURRENT_PERIOD_END_KEY) || '');
      setAccountSetupComplete(localStorage.getItem(ACCOUNT_SETUP_KEY) === 'true');
      setEmailVerified(localStorage.getItem(EMAIL_VERIFIED_KEY) === 'true');
    };

    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, []);

  function saveSession(authPayload, nextUser) {
    const nextToken = authPayload?.token || '';
    const nextRefreshToken = authPayload?.refreshToken || '';
    const nextPremium = Boolean(authPayload?.premiumUser);
    const nextSubscriptionStatus = authPayload?.subscriptionStatus || 'none';
    const nextSubscriptionId = authPayload?.stripeSubscriptionId || '';
    const nextCancelAtPeriodEnd = Boolean(authPayload?.cancelAtPeriodEnd);
    const nextCancelAt = authPayload?.cancelAt || '';
    const nextCurrentPeriodEnd = authPayload?.currentPeriodEnd || '';
    const nextEmailVerified = Boolean(authPayload?.emailVerified);
    const nextAccountSetupComplete = Boolean(authPayload?.accountSetupComplete);

    localStorage.setItem(TOKEN_KEY, nextToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, nextRefreshToken);
    localStorage.setItem(USER_KEY, nextUser);
    localStorage.setItem(PREMIUM_KEY, String(nextPremium));
    localStorage.setItem(SUBSCRIPTION_STATUS_KEY, nextSubscriptionStatus);
    localStorage.setItem(SUBSCRIPTION_ID_KEY, nextSubscriptionId);
    localStorage.setItem(SUBSCRIPTION_CANCEL_AT_PERIOD_END_KEY, String(nextCancelAtPeriodEnd));
    localStorage.setItem(SUBSCRIPTION_CANCEL_AT_KEY, nextCancelAt);
    localStorage.setItem(SUBSCRIPTION_CURRENT_PERIOD_END_KEY, nextCurrentPeriodEnd);
    localStorage.setItem(EMAIL_VERIFIED_KEY, String(nextEmailVerified));
    localStorage.setItem(ACCOUNT_SETUP_KEY, String(nextAccountSetupComplete));

    setToken(nextToken);
    setCurrentUser(nextUser);
    setPremiumUser(nextPremium);
    setSubscriptionStatus(nextSubscriptionStatus);
    setSubscriptionId(nextSubscriptionId);
    setSubscriptionCancelAtPeriodEnd(nextCancelAtPeriodEnd);
    setSubscriptionCancelAt(nextCancelAt);
    setSubscriptionCurrentPeriodEnd(nextCurrentPeriodEnd);
    setEmailVerified(nextEmailVerified);
    setAccountSetupComplete(nextAccountSetupComplete);
  }

  function clearSession() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    localStorage.removeItem(STEAM64_KEY);
    localStorage.removeItem(DISCORD_USER_ID_KEY);
    localStorage.removeItem(DISCORD_USERNAME_KEY);
    localStorage.removeItem(PREMIUM_KEY);
    localStorage.removeItem(SUBSCRIPTION_STATUS_KEY);
    localStorage.removeItem(SUBSCRIPTION_ID_KEY);
    localStorage.removeItem(SUBSCRIPTION_CANCEL_AT_PERIOD_END_KEY);
    localStorage.removeItem(SUBSCRIPTION_CANCEL_AT_KEY);
    localStorage.removeItem(SUBSCRIPTION_CURRENT_PERIOD_END_KEY);
    localStorage.removeItem(EMAIL_VERIFIED_KEY);
    localStorage.removeItem(ACCOUNT_SETUP_KEY);

    setToken('');
    setCurrentUser('');
    setSteam64Id('');
    setDiscordUserId('');
    setDiscordUsername('');
    setPremiumUser(false);
    setSubscriptionStatus('none');
    setSubscriptionId('');
    setSubscriptionCancelAtPeriodEnd(false);
    setSubscriptionCancelAt('');
    setSubscriptionCurrentPeriodEnd('');
    setEmailVerified(false);
    setAccountSetupComplete(false);
  }

  async function loadAuthProfile(authToken = token) {
    if (!authToken || !isTokenValid(authToken)) {
      return;
    }

    const me = await getAuthProfile(authToken);

    const nextUser = me?.username || currentUser || '';
    const nextSteam64 = me?.steam64Id || '';
    const nextDiscordUserId = me?.discordUserId || '';
    const nextDiscordUsername = me?.discordUsername || '';

    setCurrentUser(nextUser);
    setSteam64Id(nextSteam64);
    setDiscordUserId(nextDiscordUserId);
    setDiscordUsername(nextDiscordUsername);
    localStorage.setItem(USER_KEY, nextUser);
    localStorage.setItem(STEAM64_KEY, nextSteam64);
    localStorage.setItem(DISCORD_USER_ID_KEY, nextDiscordUserId);
    localStorage.setItem(DISCORD_USERNAME_KEY, nextDiscordUsername);
  }

  function applySubscriptionState(nextSubscription) {
    const nextPremium = Boolean(nextSubscription?.premiumUser);
    const nextSubscriptionStatus = nextSubscription?.subscriptionStatus || 'none';
    const nextSubscriptionId = nextSubscription?.stripeSubscriptionId || '';
    const nextCancelAtPeriodEnd = Boolean(nextSubscription?.cancelAtPeriodEnd);
    const nextCancelAt = nextSubscription?.cancelAt || '';
    const nextCurrentPeriodEnd = nextSubscription?.currentPeriodEnd || '';
    const nextAccountSetupComplete = Boolean(nextSubscription?.accountSetupComplete ?? accountSetupComplete);
    const nextEmailVerified = Boolean(nextSubscription?.emailVerified ?? emailVerified);

    setPremiumUser(nextPremium);
    setSubscriptionStatus(nextSubscriptionStatus);
    setSubscriptionId(nextSubscriptionId);
    setSubscriptionCancelAtPeriodEnd(nextCancelAtPeriodEnd);
    setSubscriptionCancelAt(nextCancelAt);
    setSubscriptionCurrentPeriodEnd(nextCurrentPeriodEnd);
    setAccountSetupComplete(nextAccountSetupComplete);
    setEmailVerified(nextEmailVerified);

    localStorage.setItem(PREMIUM_KEY, String(nextPremium));
    localStorage.setItem(SUBSCRIPTION_STATUS_KEY, nextSubscriptionStatus);
    localStorage.setItem(SUBSCRIPTION_ID_KEY, nextSubscriptionId);
    localStorage.setItem(SUBSCRIPTION_CANCEL_AT_PERIOD_END_KEY, String(nextCancelAtPeriodEnd));
    localStorage.setItem(SUBSCRIPTION_CANCEL_AT_KEY, nextCancelAt);
    localStorage.setItem(SUBSCRIPTION_CURRENT_PERIOD_END_KEY, nextCurrentPeriodEnd);
    localStorage.setItem(ACCOUNT_SETUP_KEY, String(nextAccountSetupComplete));
    localStorage.setItem(EMAIL_VERIFIED_KEY, String(nextEmailVerified));
  }

  return {
    token,
    currentUser,
    steam64Id,
    discordUserId,
    discordUsername,
    premiumUser,
    subscriptionStatus,
    subscriptionId,
    subscriptionCancelAtPeriodEnd,
    subscriptionCancelAt,
    subscriptionCurrentPeriodEnd,
    accountSetupComplete,
    emailVerified,
    authenticated,
    sessionId,
    setToken,
    saveSession,
    clearSession,
    loadAuthProfile,
    applySubscriptionState,
  };
}
