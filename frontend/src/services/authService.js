import { callJson } from './apiClient';

// Handled by the backend, which sends the browser to the shared sign-in
export const SIGN_IN_PATH = '/oauth2/authorization/shared';

// Ends this site's session; returns the URL that also ends the shared sign-in
export async function signOutOfServer() {
  const result = await callJson('/auth/logout', { method: 'POST' });
  return result?.logoutUrl || '/';
}

export async function getDiscordLinkUrl() {
  const response = await callJson('/auth/discord/link-url');
  if (!response?.url) {
    throw new Error('discord_link_url_missing');
  }
  return response.url;
}

export async function getGithubLinkUrl() {
  const response = await callJson('/auth/github/link-url');
  if (!response?.url) {
    throw new Error('github_link_url_missing');
  }
  return response.url;
}

export async function getAuthProfile() {
  return callJson('/auth/me');
}
