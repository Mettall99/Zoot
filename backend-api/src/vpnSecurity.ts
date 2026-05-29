import crypto from 'node:crypto';

const TOKEN_BYTES = 32;
const IV_BYTES = 12;
const AUTH_TAG_BYTES = 16;
const KEY_BYTES = 32;

const requiredEnv = (name: string): string => {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`${name} is not set`);
  return value;
};

const credentialKey = (): Buffer => {
  const raw = requiredEnv('VPN_CREDENTIALS_ENCRYPTION_KEY');
  const base64 = Buffer.from(raw, 'base64');
  if (base64.length === KEY_BYTES) return base64;
  const utf8 = Buffer.from(raw, 'utf8');
  if (utf8.length === KEY_BYTES) return utf8;
  return crypto.createHash('sha256').update(utf8).digest();
};

export const generateConfigToken = (): string => crypto.randomBytes(TOKEN_BYTES).toString('base64url');

export const tokenPrefix = (token: string): string => token.slice(0, 8);

export const hashConfigToken = (token: string): string =>
  crypto.createHmac('sha256', requiredEnv('VPN_CONFIG_TOKEN_SECRET')).update(token).digest('hex');

export const encryptConnectUri = (connectUri: string): string => {
  const iv = crypto.randomBytes(IV_BYTES);
  const cipher = crypto.createCipheriv('aes-256-gcm', credentialKey(), iv);
  const encrypted = Buffer.concat([cipher.update(connectUri, 'utf8'), cipher.final()]);
  const tag = cipher.getAuthTag();
  return `v1:${Buffer.concat([iv, tag, encrypted]).toString('base64url')}`;
};

export const decryptConnectUri = (encryptedConnectUri: string): string => {
  if (!encryptedConnectUri.startsWith('v1:')) throw new Error('Unsupported encrypted credential version');
  const data = Buffer.from(encryptedConnectUri.slice(3), 'base64url');
  const iv = data.subarray(0, IV_BYTES);
  const tag = data.subarray(IV_BYTES, IV_BYTES + AUTH_TAG_BYTES);
  const encrypted = data.subarray(IV_BYTES + AUTH_TAG_BYTES);
  const decipher = crypto.createDecipheriv('aes-256-gcm', credentialKey(), iv);
  decipher.setAuthTag(tag);
  return Buffer.concat([decipher.update(encrypted), decipher.final()]).toString('utf8');
};

export const sanitizeSecretForLog = (message: string): string => message
  .replace(/ss:\/\/[^\s,;)]*/gi, 'ss://<redacted>')
  .replace(/(password|passwd|token)[=:/][^\s,;)]*/gi, '$1=<redacted>')
  .slice(0, 500);
