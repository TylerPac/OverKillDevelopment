import { useEffect, useMemo, useState } from 'react';
import {
  ACCOUNT_SETUP_KEY,
  DISCORD_USER_ID_KEY,
  DISCORD_USERNAME_KEY,
  EMAIL_VERIFIED_KEY,
  GITHUB_USERNAME_KEY,
  REFRESH_TOKEN_KEY,
  STEAM64_KEY,
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
  const [githubUsername, setGithubUsername] = useState(() => localStorage.getItem(GITHUB_USERNAME_KEY) || '');
  const [githubReposByProduct, setGithubReposByProduct] = useState({});
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
      setAccountSetupComplete(localStorage.getItem(ACCOUNT_SETUP_KEY) === 'true');
      setEmailVerified(localStorage.getItem(EMAIL_VERIFIED_KEY) === 'true');
    };

    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, []);

  function saveSession(authPayload, nextUser) {
    const nextToken = authPayload?.token || '';
    const nextRefreshToken = authPayload?.refreshToken || '';
    const nextEmailVerified = Boolean(authPayload?.emailVerified);
    const nextAccountSetupComplete = Boolean(authPayload?.accountSetupComplete);

    localStorage.setItem(TOKEN_KEY, nextToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, nextRefreshToken);
    localStorage.setItem(USER_KEY, nextUser);
    localStorage.setItem(EMAIL_VERIFIED_KEY, String(nextEmailVerified));
    localStorage.setItem(ACCOUNT_SETUP_KEY, String(nextAccountSetupComplete));

    setToken(nextToken);
    setCurrentUser(nextUser);
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
    localStorage.removeItem(GITHUB_USERNAME_KEY);
    localStorage.removeItem(EMAIL_VERIFIED_KEY);
    localStorage.removeItem(ACCOUNT_SETUP_KEY);

    setToken('');
    setCurrentUser('');
    setSteam64Id('');
    setDiscordUserId('');
    setDiscordUsername('');
    setGithubUsername('');
    setGithubReposByProduct({});
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
    const nextGithubUsername = me?.githubUsername || '';

    setCurrentUser(nextUser);
    setSteam64Id(nextSteam64);
    setDiscordUserId(nextDiscordUserId);
    setDiscordUsername(nextDiscordUsername);
    setGithubUsername(nextGithubUsername);
    setGithubReposByProduct(me?.githubReposByProduct || {});
    localStorage.setItem(USER_KEY, nextUser);
    localStorage.setItem(STEAM64_KEY, nextSteam64);
    localStorage.setItem(DISCORD_USER_ID_KEY, nextDiscordUserId);
    localStorage.setItem(DISCORD_USERNAME_KEY, nextDiscordUsername);
    localStorage.setItem(GITHUB_USERNAME_KEY, nextGithubUsername);
  }

  return {
    token,
    currentUser,
    steam64Id,
    discordUserId,
    discordUsername,
    githubUsername,
    githubReposByProduct,
    accountSetupComplete,
    emailVerified,
    authenticated,
    sessionId,
    setToken,
    saveSession,
    clearSession,
    loadAuthProfile,
  };
}
