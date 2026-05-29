import { z } from 'zod';
import { OutlineManagementClient } from './outlineManagementClient.js';
import { decryptConnectUri, encryptConnectUri, generateConfigToken, hashConfigToken, sanitizeSecretForLog, tokenPrefix } from './vpnSecurity.js';

export const VPN_PROTOCOLS = ['outline_shadowsocks', 'wireguard', 'vless_reality'] as const;
export type VpnProtocol = typeof VPN_PROTOCOLS[number];

export const createConfigTokenSchema = z.object({
  userId: z.string().min(1).max(128),
  protocols: z.array(z.enum(VPN_PROTOCOLS)).nonempty(),
  serverId: z.string().min(1).max(128).optional()
});

export const resolveVpnTokenSchema = z.object({ token: z.string().min(16).max(512) });

export const revokeTokenParamsSchema = z.object({ id: z.string().uuid() });

export class VpnConfigTokenService {
  constructor(private readonly db: any, private readonly outline = new OutlineManagementClient()) {}

  async create(input: z.infer<typeof createConfigTokenSchema>) {
    const rawToken = generateConfigToken();
    const prefix = tokenPrefix(rawToken);
    const serverId = input.serverId || process.env.VPN_DEFAULT_SERVER_ID || 'outline-main-1';
    const tokenHash = hashConfigToken(rawToken);
    const transactional = Boolean(this.db.connect);
    const client = transactional ? await this.db.connect() : this.db;
    const release = typeof client.release === 'function' ? () => client.release() : () => undefined;
    let outlineKeyId: string | null = null;
    try {
      if (transactional) await client.query('BEGIN');
      const tokenRes = await client.query(
        `INSERT INTO vpn_config_tokens (id, user_id, token_hash, token_prefix, status, expires_at, created_at, updated_at)
         VALUES (gen_random_uuid(), $1, $2, $3, 'active', NULL, NOW(), NOW())
         RETURNING id`,
        [input.userId, tokenHash, prefix]
      );
      const configTokenId = tokenRes.rows[0].id;
      const createdProtocols: string[] = [];
      if (input.protocols.includes('outline_shadowsocks')) {
        const key = await this.outline.createAccessKey(input.userId);
        outlineKeyId = key.id;
        await this.outline.renameAccessKey(key.id, `Zooot user ${input.userId} token ${prefix}`).catch((error) => {
          console.warn(sanitizeSecretForLog(`outline rename skipped tokenPrefix=${prefix}: ${error.message}`));
        });
        await client.query(
          `INSERT INTO vpn_protocol_credentials
             (id, user_id, config_token_id, protocol, server_id, external_key_id, encrypted_connect_uri, status, last_error, created_at, updated_at)
           VALUES (gen_random_uuid(), $1, $2, 'outline_shadowsocks', $3, $4, $5, 'active', NULL, NOW(), NOW())`,
          [input.userId, configTokenId, serverId, key.id, encryptConnectUri(key.accessUrl)]
        );
        createdProtocols.push('outline_shadowsocks');
      }
      if (transactional) await client.query('COMMIT');
      console.info(`vpn config token created tokenPrefix=${prefix} protocols=${createdProtocols.join(',')}`);
      return { id: configTokenId, configUrl: `zoootconf://${rawToken}`, protocols: createdProtocols };
    } catch (error: any) {
      if (transactional) await client.query('ROLLBACK').catch(() => undefined);
      if (outlineKeyId) await this.outline.deleteAccessKey(outlineKeyId).catch(() => undefined);
      console.warn(sanitizeSecretForLog(`vpn config token create failed tokenPrefix=${prefix}: ${error?.message || error}`));
      throw error;
    } finally {
      release();
    }
  }

  async resolve(rawToken: string) {
    const tokenHash = hashConfigToken(rawToken);
    const tokenRes = await this.db.query(
      `SELECT id, user_id, token_prefix, status, expires_at
       FROM vpn_config_tokens
       WHERE token_hash = $1
       LIMIT 1`,
      [tokenHash]
    );
    if (!tokenRes.rowCount) return { statusCode: 404, body: { error: 'INVALID_CONFIG_TOKEN', message: 'Invalid config token' } };
    const token = tokenRes.rows[0];
    if (token.status === 'revoked') return { statusCode: 403, body: { error: 'CONFIG_TOKEN_REVOKED', message: 'Config token revoked' } };
    if (token.status === 'expired' || (token.expires_at && new Date(token.expires_at).getTime() <= Date.now())) {
      return { statusCode: 403, body: { error: 'CONFIG_TOKEN_EXPIRED', message: 'Config token expired' } };
    }

    const subscription = await this.db.query(
      `SELECT id FROM subscriptions WHERE user_id = $1 AND status = 'active' AND expires_at > NOW() LIMIT 1`,
      [token.user_id]
    );
    if (subscription.rowCount === 0 && process.env.VPN_CONFIG_REQUIRE_ACTIVE_SUBSCRIPTION === 'true') {
      return { statusCode: 403, body: { error: 'SUBSCRIPTION_INACTIVE', message: 'Subscription is not active' } };
    }

    const credentials = await this.db.query(
      `SELECT protocol, server_id, external_key_id, encrypted_connect_uri, status, last_error
       FROM vpn_protocol_credentials
       WHERE config_token_id = $1
       ORDER BY protocol = 'outline_shadowsocks' DESC, created_at ASC`,
      [token.id]
    );
    const protocols = [];
    for (const credential of credentials.rows) {
      if (credential.status === 'revoked') continue;
      if (credential.protocol === 'outline_shadowsocks') {
        if (credential.status !== 'active' || !credential.encrypted_connect_uri) {
          return { statusCode: 503, body: { error: 'OUTLINE_SHADOWSOCKS_UNAVAILABLE', message: 'Outline Shadowsocks key is not available' } };
        }
        protocols.push({
          type: 'outline_shadowsocks',
          displayName: 'Outline Shadowsocks',
          connectUri: decryptConnectUri(credential.encrypted_connect_uri),
          config: decryptConnectUri(credential.encrypted_connect_uri),
          config_source: 'server_generated',
          server: { id: credential.server_id, country: process.env.VPN_DEFAULT_SERVER_COUNTRY || 'DE', city: null }
        });
      }
    }
    if (protocols.length === 0) return { statusCode: 404, body: { error: 'NO_VPN_PROTOCOLS_AVAILABLE', message: 'No VPN protocols available' } };
    console.info(`vpn config resolved tokenPrefix=${token.token_prefix} protocol=${protocols[0].type}`);
    return { statusCode: 200, body: { status: 'active', recommendedProtocol: protocols[0].type, protocols } };
  }

  async revoke(id: string) {
    const transactional = Boolean(this.db.connect);
    const client = transactional ? await this.db.connect() : this.db;
    const release = typeof client.release === 'function' ? () => client.release() : () => undefined;
    try {
      if (transactional) await client.query('BEGIN');
      const tokenRes = await client.query(
        `UPDATE vpn_config_tokens SET status = 'revoked', updated_at = NOW() WHERE id = $1 RETURNING id, token_prefix`,
        [id]
      );
      if (!tokenRes.rowCount) return { statusCode: 404, body: { error: 'CONFIG_TOKEN_NOT_FOUND', message: 'Invalid config token' } };
      const creds = await client.query(
        `UPDATE vpn_protocol_credentials SET status = 'revoked', updated_at = NOW()
         WHERE config_token_id = $1
         RETURNING protocol, external_key_id`,
        [id]
      );
      for (const cred of creds.rows) {
        if (cred.protocol === 'outline_shadowsocks' && cred.external_key_id) await this.outline.deleteAccessKey(cred.external_key_id);
      }
      if (transactional) await client.query('COMMIT');
      console.info(`vpn config revoked tokenPrefix=${tokenRes.rows[0].token_prefix}`);
      return { statusCode: 200, body: { status: 'revoked' } };
    } catch (error) {
      if (transactional) await client.query('ROLLBACK').catch(() => undefined);
      throw error;
    } finally {
      release();
    }
  }
}
