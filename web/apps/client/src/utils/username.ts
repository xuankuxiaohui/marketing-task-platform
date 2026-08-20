const USERNAME = /^[a-z0-9_]{4,30}$/;
const NICKNAME = /^[\u4e00-\u9fa5a-zA-Z0-9_]{1,30}$/;

export function portalUsernameSatisfied(username: string): boolean {
  return USERNAME.test(username.trim().toLowerCase());
}

export function portalNicknameSatisfied(nickname: string): boolean {
  return NICKNAME.test(nickname.trim());
}
