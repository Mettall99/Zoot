import 'dotenv/config';
import express from 'express';
import cors from 'cors';
import jwt from 'jsonwebtoken';
import argon2 from 'argon2';
import { z } from 'zod';
import fs from 'node:fs/promises';
import { getDb } from './db.js';
import { apiError, zodToValidation } from './errors.js';
import { DEFAULT_DEVICE_ID, getOrCreateWireGuardDeviceConfig, isProvisioningEnabled, normalizeDeviceInput } from './wireguard/provisioning.js';

export const app = express();
app.use(cors());
app.use(express.json());

const authSchema = z.object({ email: z.string().email(), password: z.string().min(8) });
const resolveTokenSchema = z.object({ token: z.string().min(1), device_id: z.string().optional(), device_name: z.string().optional() });
const MAX_WIREGUARD_CONFIG_BYTES = 64 * 1024;

const isValidConfig = (config: unknown): config is string => typeof config === 'string' && config.trim().length > 0 && config.trim().toLowerCase() !== 'null';

const readWireGuardClientConfig = async (): Promise<string | null> => {
  const path = process.env.WIREGUARD_CLIENT_CONFIG_PATH?.trim();
  if (!path) return null;

  try {
    const stat = await fs.stat(path);
    if (!stat.isFile() || stat.size > MAX_WIREGUARD_CONFIG_BYTES) return null;
    return await fs.readFile(path, 'utf8');
  } catch (error: any) {
    if (error?.code === 'ENOENT' || error?.code === 'EACCES' || error?.code === 'EPERM') return null;
    return null;
  }
};

app.get('/health', (_req, res) => res.json({ ok: true, service: 'zooot-backend-api' }));

app.post('/api/v1/auth/register', async (req, res) => {
  const parsed = authSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json(zodToValidation(parsed.error));

  const db = getDb();
  const exists = await db.query('SELECT id FROM users WHERE email = $1 LIMIT 1', [parsed.data.email]);
  if (exists.rowCount) return res.status(409).json(apiError('USER_ALREADY_EXISTS', 'Email already registered'));

  const passwordHash = await argon2.hash(parsed.data.password);
  const result = await db.query(
    `INSERT INTO users (id, email, password_hash, created_at, updated_at)
     VALUES (gen_random_uuid(), $1, $2, NOW(), NOW())
     RETURNING id, email`,
    [parsed.data.email, passwordHash]
  );

  return res.status(201).json({ userId: result.rows[0].id, email: result.rows[0].email });
});

app.post('/api/v1/auth/login', async (req, res) => {
  const parsed = authSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json(zodToValidation(parsed.error));

  const db = getDb();
  const user = await db.query('SELECT id, password_hash FROM users WHERE email = $1 LIMIT 1', [parsed.data.email]);
  if (!user.rowCount) return res.status(401).json(apiError('INVALID_CREDENTIALS', 'Invalid email or password'));

  const ok = await argon2.verify(user.rows[0].password_hash, parsed.data.password);
  if (!ok) return res.status(401).json(apiError('INVALID_CREDENTIALS', 'Invalid email or password'));

  const accessToken = jwt.sign({ sub: user.rows[0].id }, process.env.JWT_SECRET || 'dev', { expiresIn: '15m' });
  const refreshToken = jwt.sign({ sub: user.rows[0].id }, process.env.JWT_REFRESH_SECRET || 'dev2', { expiresIn: '7d' });
  return res.json({ accessToken, refreshToken });
});

app.post('/api/v1/config/resolve-token', async (req, res) => {
  const parsed = resolveTokenSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json(zodToValidation(parsed.error));

  let deviceId = DEFAULT_DEVICE_ID;
  const hasDeviceId = typeof parsed.data.device_id === 'string' && parsed.data.device_id.trim().length > 0;
  let deviceName: string | null = null;
  try {
    const normalized = normalizeDeviceInput(parsed.data.device_id, parsed.data.device_name);
    deviceId = normalized.deviceId;
    deviceName = normalized.deviceName;
  } catch {
    return res.status(400).json(apiError('INVALID_DEVICE_ID', 'Invalid device_id format'));
  }

  const db = getDb();
  const tokenRes = await db.query(
    `SELECT ct.user_id, u.email, u.id, s.tariff_id, s.status AS subscription_status, s.expires_at, t.code AS tariff_code, t.title AS tariff_title,
            ct.preferred_country
     FROM config_tokens ct
     JOIN users u ON u.id = ct.user_id
     LEFT JOIN subscriptions s ON s.user_id = u.id AND s.status = 'active' AND s.expires_at > NOW()
     LEFT JOIN tariffs t ON t.id = s.tariff_id
     WHERE ct.token = $1 AND ct.revoked_at IS NULL AND ct.expires_at > NOW()
     LIMIT 1`,
    [parsed.data.token]
  );

  if (!tokenRes.rowCount) return res.status(404).json(apiError('CONFIG_TOKEN_NOT_FOUND', 'Config token not found'));
  const row = tokenRes.rows[0];
  if (!row.tariff_id) return res.status(403).json(apiError('SUBSCRIPTION_INACTIVE', 'Subscription is inactive'));
  const serversRes = await db.query(
    `SELECT s.id, s.country_code, s.city, s.ip::text, s.load_percent,
            sp.type, sp.priority, sp.port, sp.status AS health_status
     FROM servers s
     JOIN server_protocols sp ON sp.server_id = s.id
     WHERE s.status = 'online' AND sp.status = 'healthy'
     ORDER BY s.load_percent ASC, sp.priority ASC`
  );
  const demoWireguardConfig = await readWireGuardClientConfig();

  const serversMap = new Map<string, any>();
  for (const r of serversRes.rows) {
    if (!serversMap.has(r.id)) {
      serversMap.set(r.id, { id: r.id, country: r.country_code, city: r.city, ip: r.ip, load_percent: r.load_percent, protocols: [] });
    }
    let wireguardResponse = { config: null as string | null, config_source: null as string | null };
    if (r.type === 'wireguard') {
      if (hasDeviceId && isProvisioningEnabled()) {
        try {
          const result = await getOrCreateWireGuardDeviceConfig(db, row.user_id, r.id, deviceId, deviceName);
          if (isValidConfig(result.config)) {
            wireguardResponse = { config: result.config, config_source: result.configSource };
          } else {
            console.warn('wireguard device config unavailable, using fallback');
            wireguardResponse = isValidConfig(demoWireguardConfig)
              ? { config: demoWireguardConfig, config_source: 'demo_fallback' }
              : { config: null, config_source: null };
          }
        } catch {
          wireguardResponse = isValidConfig(demoWireguardConfig)
            ? { config: demoWireguardConfig, config_source: 'demo_fallback' }
            : { config: null, config_source: null };
        }
      } else {
        wireguardResponse = isValidConfig(demoWireguardConfig)
          ? { config: demoWireguardConfig, config_source: 'demo_fallback' }
          : { config: null, config_source: null };
      }
    }
    serversMap.get(r.id).protocols.push({
      type: r.type,
      priority: r.priority,
      port: r.port,
      health_status: r.health_status,
      config: wireguardResponse.config,
      config_source: r.type === 'wireguard' ? wireguardResponse.config_source : null
    });
  }

  return res.json({
    user: { id: row.id, email: row.email },
    tariff: { id: row.tariff_id, code: row.tariff_code, title: row.tariff_title },
    preferred_country: row.preferred_country,
    servers: Array.from(serversMap.values())
  });
});

app.get('/api/v1/servers/recommended', async (_req, res) => {
  const db = getDb();
  const rows = await db.query(
    `SELECT s.id, s.country_code, s.city, s.ip::text, s.load_percent,
            sp.type, sp.priority, sp.port, sp.status AS health_status
     FROM servers s
     JOIN server_protocols sp ON sp.server_id = s.id
     WHERE s.status = 'online' AND sp.status = 'healthy'
     ORDER BY s.load_percent ASC, sp.priority ASC`
  );

  const serversMap = new Map<string, any>();
  for (const r of rows.rows) {
    if (!serversMap.has(r.id)) {
      serversMap.set(r.id, { id: r.id, country: r.country_code, city: r.city, ip: r.ip, load_percent: r.load_percent, protocols: [] });
    }
    serversMap.get(r.id).protocols.push({ type: r.type, priority: r.priority, port: r.port, health_status: r.health_status });
  }

  return res.json({ servers: Array.from(serversMap.values()) });
});

if (process.env.NODE_ENV !== 'test') {
  const port = Number(process.env.PORT || 8080);
  app.listen(port, () => {
    console.log(`zooot-backend-api listening on :${port}`);
  });
}
