import { useEffect, useRef, useState } from 'react';
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
import { SESSION_EXPIRED_EVENT } from '../services/apiClient';

// Removes what earlier versions stored, including sign-in tokens that are no longer used.
function removeLegacyStorage() {
  [
    TOKEN_KEY,
    REFRESH_TOKEN_KEY,
    USER_KEY,
    STEAM64_KEY,
    DISCORD_USER_ID_KEY,
    DISCORD_USERNAME_KEY,
    GITHUB_USERNAME_KEY,
    EMAIL_VERIFIED_KEY,
    ACCOUNT_SETUP_KEY,
  ].forEach((key) => localStorage.removeItem(key));
}

// The server holds the session; this mirrors who is signed in. `ready` is false until the first check finishes.
export default function useSessionState(onSessionExpired) {
  const [ready, setReady] = useState(false);
  const [currentUser, setCurrentUser] = useState('');
  const [steam64Id, setSteam64Id] = useState('');
  const [discordUserId, setDiscordUserId] = useState('');
  const [discordUsername, setDiscordUsername] = useState('');
  const [githubUsername, setGithubUsername] = useState('');
  const [githubReposByProduct, setGithubReposByProduct] = useState({});
  const [accountSetupComplete, setAccountSetupComplete] = useState(false);
  const [emailVerified, setEmailVerified] = useState(false);
  // From the backend, which asks the shared login service; never decided in the browser
  const [admin, setAdmin] = useState(false);

  const authenticated = Boolean(currentUser);
  const signedIn = useRef(false);
  const expiredCallback = useRef(onSessionExpired);

  useEffect(() => {
    signedIn.current = authenticated;
    expiredCallback.current = onSessionExpired;
  });

  function clearSession() {
    setCurrentUser('');
    setSteam64Id('');
    setDiscordUserId('');
    setDiscordUsername('');
    setGithubUsername('');
    setGithubReposByProduct({});
    setAccountSetupComplete(false);
    setEmailVerified(false);
    setAdmin(false);
  }

  function applyProfile(me) {
    setCurrentUser(me?.username || '');
    setSteam64Id(me?.steam64Id || '');
    setDiscordUserId(me?.discordUserId || '');
    setDiscordUsername(me?.discordUsername || '');
    setGithubUsername(me?.githubUsername || '');
    setGithubReposByProduct(me?.githubReposByProduct || {});
    setEmailVerified(Boolean(me?.emailVerified));
    setAccountSetupComplete(Boolean(me?.accountSetupComplete));
    setAdmin(me?.admin === true);
  }

  useEffect(() => {
    removeLegacyStorage();
    getAuthProfile()
      .then(applyProfile)
      .catch(() => undefined) // Not signed in
      .finally(() => setReady(true));

    const onExpired = () => {
      if (!signedIn.current) return;
      clearSession();
      expiredCallback.current?.('Session expired. Please sign in with Steam again.');
    };
    window.addEventListener(SESSION_EXPIRED_EVENT, onExpired);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, onExpired);
  }, []);

  async function loadAuthProfile() {
    applyProfile(await getAuthProfile());
  }

  return {
    ready,
    currentUser,
    steam64Id,
    discordUserId,
    discordUsername,
    githubUsername,
    githubReposByProduct,
    accountSetupComplete,
    emailVerified,
    admin,
    authenticated,
    clearSession,
    loadAuthProfile,
  };
}
