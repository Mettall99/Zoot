import fs from 'node:fs/promises';
import path from 'node:path';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';

const execFileAsync = promisify(execFile);
const MAX_WIREGUARD_CONFIG_BYTES = 64 * 1024;
const DEVICE_ID_REGEX = /^[a-zA-Z0-9._-]{1,80}$/;

export const DEFAULT_DEVICE_ID = 'default-demo-device';

export type WireGuardDeviceRecord = {
  id: string;
  assigned_ip: string;
  public_key: string;
  config_path: string | null;
};

export const normalizeDeviceInput = (deviceId?: string | null, deviceName?: string | null) => {
  const normalizedId = (deviceId || DEFAULT_DEVICE_ID).trim();
  if (!DEVICE_ID_REGEX.test(normalizedId)) {
    throw new Error('INVALID_DEVICE_ID');
  }
  return {
    deviceId: normalizedId,
    deviceName: deviceName?.trim() || null
  };
};

export const isProvisioningEnabled = () => process.env.WIREGUARD_PROVISIONING_ENABLED === 'true';

const readConfigIfSafe = async (configPath: string | null): Promise<string | null> => {
  if (!configPath) return null;
  try {
    const stat = await fs.stat(configPath);
    if (!stat.isFile() || stat.size > MAX_WIREGUARD_CONFIG_BYTES) return null;
    return await fs.readFile(configPath, 'utf8');
  } catch {
    return null;
  }
};

const safeClientName = (userId: string, deviceId: string): string => `user_${userId.slice(0, 8)}_${deviceId}`;

export const findActiveDeviceConfig = async (db: any, userId: string, serverId: string, deviceId: string): Promise<WireGuardDeviceRecord | null> => {
  const result = await db.query(
    `SELECT id, assigned_ip::text, public_key, config_path
     FROM wireguard_devices
     WHERE user_id = $1 AND server_id = $2 AND device_id = $3 AND status = 'active'
     LIMIT 1`,
    [userId, serverId, deviceId]
  );
  return result.rowCount ? result.rows[0] : null;
};

export const createDeviceConfig = async (db: any, userId: string, serverId: string, deviceId: string, deviceName: string | null) => {
  const script = process.env.WIREGUARD_GENERATE_CLIENT_SCRIPT?.trim();
  const clientsDir = process.env.WIREGUARD_CLIENTS_DIR?.trim();
  if (!script || !clientsDir) throw new Error('PROVISIONING_NOT_CONFIGURED');

  const clientName = safeClientName(userId, deviceId);
  const { stdout } = await execFileAsync(script, [clientName], { timeout: 10_000 });

  const assignedIp = stdout.match(/assigned_ip=(\S+)/)?.[1] ?? '';
  const publicKey = stdout.match(/public_key=(\S+)/)?.[1] ?? '';
  const configPath = path.join(clientsDir, `${clientName}.conf`);
  if (!assignedIp || !publicKey) throw new Error('PROVISIONING_INVALID_OUTPUT');

  const inserted = await db.query(
    `INSERT INTO wireguard_devices (user_id, server_id, device_id, device_name, assigned_ip, public_key, config_path, status, created_at, updated_at)
     VALUES ($1, $2, $3, $4, $5::inet, $6, $7, 'active', NOW(), NOW())
     RETURNING id, assigned_ip::text, public_key, config_path`,
    [userId, serverId, deviceId, deviceName, assignedIp, publicKey, configPath]
  );
  return inserted.rows[0] as WireGuardDeviceRecord;
};

export const revokeDeviceConfig = async (db: any, id: string) => {
  await db.query(`UPDATE wireguard_devices SET status='revoked', revoked_at=NOW(), updated_at=NOW() WHERE id=$1`, [id]);
};

export const getOrCreateWireGuardDeviceConfig = async (db: any, userId: string, serverId: string, deviceId: string, deviceName: string | null) => {
  const existing = await findActiveDeviceConfig(db, userId, serverId, deviceId);
  if (existing) {
    return { record: existing, config: await readConfigIfSafe(existing.config_path), configSource: 'device' as const };
  }
  const created = await createDeviceConfig(db, userId, serverId, deviceId, deviceName);
  return { record: created, config: await readConfigIfSafe(created.config_path), configSource: 'device' as const };
};
