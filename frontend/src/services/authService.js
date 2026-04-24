import { callJson } from './apiClient';

export async function getSteamLoginUrl() {
  const response = await callJson('/auth/steam/login-url');
  if (!response?.url) {
    throw new Error('steam_login_url_missing');
  }
  return response.url;
}

export async function getDiscordLinkUrl(token) {
  const response = await callJson('/auth/discord/link-url', {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
  if (!response?.url) {
    throw new Error('discord_link_url_missing');
  }
  return response.url;
}

export async function getGithubLinkUrl(token) {
  const response = await callJson('/auth/github/link-url', {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
  if (!response?.url) {
    throw new Error('github_link_url_missing');
  }
  return response.url;
}

export async function getAuthProfile(token) {
  return callJson('/auth/me', {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}
