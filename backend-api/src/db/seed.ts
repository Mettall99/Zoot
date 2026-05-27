import 'dotenv/config';
import argon2 from 'argon2';
import { getDb, closeDb } from '../db.js';

const run = async () => {
  const db = getDb();
  const passwordHash = await argon2.hash('password123');

  const user = await db.query(
    `INSERT INTO users (id, email, password_hash, created_at, updated_at)
     VALUES (gen_random_uuid(), 'demo@zooot.local', $1, NOW(), NOW())
     ON CONFLICT (email) DO UPDATE SET password_hash = EXCLUDED.password_hash, updated_at = NOW()
     RETURNING id`,
    [passwordHash]
  );

  const userId = user.rows[0].id;

  const tariff = await db.query(
    `INSERT INTO tariffs (id, code, title, device_limit, price_cents, period_days)
     VALUES (gen_random_uuid(), 'demo-monthly', 'Demo Monthly', 5, 999, 30)
     ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title
     RETURNING id`
  );

  const tariffId = tariff.rows[0].id;

  await db.query(
    `INSERT INTO subscriptions (id, user_id, tariff_id, status, starts_at, expires_at)
     VALUES (gen_random_uuid(), $1, $2, 'active', NOW(), NOW() + INTERVAL '30 days')
     ON CONFLICT DO NOTHING`,
    [userId, tariffId]
  );

  await db.query(
    `INSERT INTO config_tokens (user_id, token, preferred_country, expires_at)
     VALUES ($1, 'demo-token', 'DE', NOW() + INTERVAL '30 days')
     ON CONFLICT (token) DO UPDATE SET user_id = EXCLUDED.user_id, revoked_at = NULL, expires_at = EXCLUDED.expires_at`,
    [userId]
  );

  // TODO: replace static geo metadata (country/city/name) with real geo lookup for server endpoint.
  const serverName = 'de-frankfurt-1';
  const serverIp = '31.59.45.197';

  const existingServer = await db.query(
    `SELECT id
     FROM servers
     WHERE ip = $1 OR name = $2
     ORDER BY CASE WHEN ip = $1 THEN 0 ELSE 1 END
     LIMIT 1`,
    [serverIp, serverName]
  );

  let serverId: string;
  if (existingServer.rowCount) {
    const updated = await db.query(
      `UPDATE servers
       SET name = $2,
           country_code = 'DE',
           city = 'Frankfurt',
           ip = $1,
           status = 'online',
           load_percent = 20,
           max_users = 500,
           active_users = 120
       WHERE id = $3
       RETURNING id`,
      [serverIp, serverName, existingServer.rows[0].id]
    );
    serverId = updated.rows[0].id;
  } else {
    const inserted = await db.query(
      `INSERT INTO servers (id, name, country_code, city, ip, status, load_percent, max_users, active_users)
       VALUES (gen_random_uuid(), $1, 'DE', 'Frankfurt', $2, 'online', 20, 500, 120)
       RETURNING id`,
      [serverName, serverIp]
    );
    serverId = inserted.rows[0].id;
  }

  for (const [type, priority, port] of [
    ['amneziawg', 1, 51820],
    ['xray_vless_reality', 2, 443],
    ['wireguard', 3, 51821],
    ['openvpn_udp', 4, 1194]
  ]) {
    await db.query(
      `INSERT INTO server_protocols (server_id, type, priority, port, status)
       VALUES ($1, $2, $3, $4, 'healthy')
       ON CONFLICT (server_id, type, port) DO UPDATE SET status = 'healthy', priority = EXCLUDED.priority`,
      [serverId, type, priority, port]
    );
  }

  await closeDb();
  console.log('Seed completed');
};

run().catch(async (e) => {
  console.error(e);
  await closeDb();
  process.exit(1);
});
