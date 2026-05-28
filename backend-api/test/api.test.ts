import { beforeAll, afterAll, describe, expect, it, vi, beforeEach } from 'vitest';
import os from 'node:os';
import path from 'node:path';
import fs from 'node:fs/promises';

const query = vi.fn();
vi.mock('../src/db.js', () => ({ getDb: () => ({ query }) }));
vi.mock('argon2', () => ({ default: { hash: vi.fn(async () => 'hash'), verify: vi.fn(async (h: string, p: string) => h === 'hash' && p === 'password123') } }));

import { app } from '../src/main.js';
import * as provisioning from '../src/wireguard/provisioning.js';

let base = '';
let server: any;
const post = (path: string, body: unknown) => fetch(`${base}${path}`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) });

beforeAll(async () => {
  await new Promise<void>((resolve) => {
    server = app.listen(0, () => {
      const port = server.address().port;
      base = `http://127.0.0.1:${port}`;
      resolve();
    });
  });
});
afterAll(() => server.close());

describe('auth + config api', () => {
  beforeEach(() => {
    query.mockReset();
    delete process.env.WIREGUARD_CLIENT_CONFIG_PATH;
    delete process.env.WIREGUARD_PROVISIONING_ENABLED;
    delete process.env.XRAY_REALITY_ENABLED;
    delete process.env.XRAY_REALITY_HOST;
    delete process.env.XRAY_REALITY_PUBLIC_KEY;
    delete process.env.XRAY_REALITY_SHORT_ID;
    delete process.env.XRAY_REALITY_SERVER_NAME;
    delete process.env.XRAY_REALITY_SNI;
    delete process.env.XRAY_REALITY_PORT;
    delete process.env.XRAY_REALITY_FLOW;
    delete process.env.XRAY_REALITY_UUID;
    vi.restoreAllMocks();
  });
  it('register creates user', async () => { query.mockResolvedValueOnce({ rowCount: 0, rows: [] }).mockResolvedValueOnce({ rows: [{ id: 'u1', email: 'a@b.com' }] }); const r = await post('/api/v1/auth/register',{email:'a@b.com',password:'password123'}); expect(r.status).toBe(201); });
  it('duplicate email returns 409', async () => { query.mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'u1' }] }); const r = await post('/api/v1/auth/register',{email:'a@b.com',password:'password123'}); expect(r.status).toBe(409); });
  it('login returns tokens', async () => { query.mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'u1', password_hash: 'hash' }] }); const r = await post('/api/v1/auth/login',{email:'a@b.com',password:'password123'}); expect(r.status).toBe(200); expect((await r.json()).accessToken).toBeTruthy(); });
  it('invalid login returns 401', async () => { query.mockResolvedValueOnce({ rowCount: 0, rows: [] }); const r = await post('/api/v1/auth/login',{email:'a@b.com',password:'password123'}); expect(r.status).toBe(401); });
  it('resolve demo-token returns servers', async () => { query.mockResolvedValueOnce({ rowCount: 1, rows: [{ id:'u1', email:'demo', tariff_id:'t1', tariff_code:'demo', tariff_title:'Demo', preferred_country:'DE' }] }).mockResolvedValueOnce({ rows: [{ id:'s1', country_code:'DE', city:'Frankfurt', ip:'31.59.45.197', load_percent:20, type:'amneziawg', priority:1, port:51820, health_status:'healthy' }] }); const r=await post('/api/v1/config/resolve-token',{token:'demo-token'}); expect(r.status).toBe(200); });
  it('resolve-token works without WIREGUARD_CLIENT_CONFIG_PATH', async () => {
    query.mockResolvedValueOnce({ rowCount: 1, rows: [{ id:'u1', email:'demo', tariff_id:'t1', tariff_code:'demo', tariff_title:'Demo', preferred_country:'DE' }] })
      .mockResolvedValueOnce({ rows: [{ id:'s1', country_code:'DE', city:'Frankfurt', ip:'31.59.45.197', load_percent:20, type:'wireguard', priority:3, port:51821, health_status:'healthy' }] });
    const r = await post('/api/v1/config/resolve-token', { token:'demo-token' });
    expect(r.status).toBe(200);
    const body = await r.json();
    expect(body.servers[0].protocols[0].config).toBeNull();
  });
  it('resolve-token returns wireguard config from file', async () => {
    const dir = await fs.mkdtemp(path.join(os.tmpdir(), 'zooot-wg-'));
    const file = path.join(dir, 'demo.conf');
    await fs.writeFile(file, '[Interface]\nPrivateKey = test\n');
    process.env.WIREGUARD_CLIENT_CONFIG_PATH = file;
    query.mockResolvedValueOnce({ rowCount: 1, rows: [{ id:'u1', email:'demo', tariff_id:'t1', tariff_code:'demo', tariff_title:'Demo', preferred_country:'DE' }] })
      .mockResolvedValueOnce({ rows: [{ id:'s1', country_code:'DE', city:'Frankfurt', ip:'31.59.45.197', load_percent:20, type:'wireguard', priority:3, port:51821, health_status:'healthy' }] });
    const r = await post('/api/v1/config/resolve-token', { token:'demo-token' });
    expect(r.status).toBe(200);
    const body = await r.json();
    expect(body.servers[0].protocols[0].config).toContain('PrivateKey = test');
  });
  it('resolve-token handles missing wireguard config file', async () => {
    process.env.WIREGUARD_CLIENT_CONFIG_PATH = '/tmp/zooot/missing-demo.conf';
    query.mockResolvedValueOnce({ rowCount: 1, rows: [{ id:'u1', email:'demo', tariff_id:'t1', tariff_code:'demo', tariff_title:'Demo', preferred_country:'DE' }] })
      .mockResolvedValueOnce({ rows: [{ id:'s1', country_code:'DE', city:'Frankfurt', ip:'31.59.45.197', load_percent:20, type:'wireguard', priority:3, port:51821, health_status:'healthy' }] });
    const r = await post('/api/v1/config/resolve-token', { token:'demo-token' });
    expect(r.status).toBe(200);
    const body = await r.json();
    expect(body.servers[0].protocols[0].config).toBeNull();
  });
  it('invalid token returns 404', async () => { query.mockResolvedValueOnce({ rowCount: 0, rows: [] }); const r=await post('/api/v1/config/resolve-token',{token:'x'}); expect(r.status).toBe(404); });
  it('inactive subscription returns 403', async () => { query.mockResolvedValueOnce({ rowCount: 1, rows: [{ id:'u1', email:'demo', tariff_id:null }] }); const r=await post('/api/v1/config/resolve-token',{token:'demo-token'}); expect(r.status).toBe(403); });

  it('health works', async () => { const r = await fetch(`${base}/health`); expect(r.status).toBe(200); });
});


it('resolve-token accepts missing device_id with default fallback source', async () => {
  process.env.WIREGUARD_CLIENT_CONFIG_PATH = '/tmp/zooot/missing-demo.conf';
  query.mockResolvedValueOnce({ rowCount: 1, rows: [{ user_id:'u1', id:'u1', email:'demo', tariff_id:'t1', tariff_code:'demo', tariff_title:'Demo', preferred_country:'DE' }] })
    .mockResolvedValueOnce({ rows: [{ id:'s1', country_code:'DE', city:'Frankfurt', ip:'31.59.45.197', load_percent:20, type:'wireguard', priority:3, port:51821, health_status:'healthy' }] });
  const r = await post('/api/v1/config/resolve-token', { token:'demo-token' });
  expect(r.status).toBe(200);
  const body = await r.json();
  expect(body.servers[0].protocols[0].config_source).toBeNull();
});

it('resolve-token rejects invalid device_id', async () => {
  const r = await post('/api/v1/config/resolve-token', { token:'demo-token', device_id:'bad id with space' });
  expect(r.status).toBe(400);
});


it('resolve-token never returns config_source=device with null config', async () => {
  process.env.WIREGUARD_PROVISIONING_ENABLED = 'true';
  const spyEnabled = vi.spyOn(provisioning, 'isProvisioningEnabled').mockReturnValue(true);
  const spyGet = vi.spyOn(provisioning, 'getOrCreateWireGuardDeviceConfig').mockResolvedValue({ config: null, configSource: 'device' } as any);
  query.mockResolvedValueOnce({ rowCount: 1, rows: [{ user_id:'u1', id:'u1', email:'demo', tariff_id:'t1', tariff_code:'demo', tariff_title:'Demo', preferred_country:'DE' }] })
    .mockResolvedValueOnce({ rows: [{ id:'s1', country_code:'DE', city:'Frankfurt', ip:'31.59.45.197', load_percent:20, type:'wireguard', priority:3, port:51821, health_status:'healthy' }] });
  const r = await post('/api/v1/config/resolve-token', { token:'demo-token' });
  expect(r.status).toBe(200);
  const body = await r.json();
  expect(body.servers[0].protocols[0].config_source).not.toBe('device');
  expect(body.servers[0].protocols[0].config).toBeNull();
  expect(spyEnabled).toBeDefined();
  expect(spyGet).toBeDefined();
});

it('resolve-token falls back to demo_fallback when agent returns ok but null config', async () => {
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), 'zooot-wg-fallback-'));
  const file = path.join(dir, 'demo.conf');
  await fs.writeFile(file, '[Interface]\nPrivateKey = demo\n');
  process.env.WIREGUARD_CLIENT_CONFIG_PATH = file;
  vi.spyOn(provisioning, 'isProvisioningEnabled').mockReturnValue(true);
  vi.spyOn(provisioning, 'getOrCreateWireGuardDeviceConfig').mockResolvedValue({ config: null, configSource: 'device' } as any);
  query.mockResolvedValueOnce({ rowCount: 1, rows: [{ user_id:'u1', id:'u1', email:'demo', tariff_id:'t1', tariff_code:'demo', tariff_title:'Demo', preferred_country:'DE' }] })
    .mockResolvedValueOnce({ rows: [{ id:'s1', country_code:'DE', city:'Frankfurt', ip:'31.59.45.197', load_percent:20, type:'wireguard', priority:3, port:51821, health_status:'healthy' }] });
  const r = await post('/api/v1/config/resolve-token', { token:'demo-token' });
  const body = await r.json();
  expect(body.servers[0].protocols[0].config_source).toBe('demo_fallback');
  expect(body.servers[0].protocols[0].config).toContain('PrivateKey = demo');
});

it('resolve-token uses demo_fallback when provisioning fails and fallback exists', async () => {
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), 'zooot-wg-fallback-'));
  const file = path.join(dir, 'demo.conf');
  await fs.writeFile(file, '[Interface]\nPrivateKey = demo2\n');
  process.env.WIREGUARD_CLIENT_CONFIG_PATH = file;
  vi.spyOn(provisioning, 'isProvisioningEnabled').mockReturnValue(true);
  vi.spyOn(provisioning, 'getOrCreateWireGuardDeviceConfig').mockRejectedValue(new Error('boom'));
  query.mockResolvedValueOnce({ rowCount: 1, rows: [{ user_id:'u1', id:'u1', email:'demo', tariff_id:'t1', tariff_code:'demo', tariff_title:'Demo', preferred_country:'DE' }] })
    .mockResolvedValueOnce({ rows: [{ id:'s1', country_code:'DE', city:'Frankfurt', ip:'31.59.45.197', load_percent:20, type:'wireguard', priority:3, port:51821, health_status:'healthy' }] });
  const r = await post('/api/v1/config/resolve-token', { token:'demo-token' });
  const body = await r.json();
  expect(body.servers[0].protocols[0].config_source).toBe('demo_fallback');
  expect(body.servers[0].protocols[0].config).toContain('PrivateKey = demo2');
});



it('resolve-token with device_id and successful agent returns config_source=device', async () => {
  vi.spyOn(provisioning, 'isProvisioningEnabled').mockReturnValue(true);
  vi.spyOn(provisioning, 'getOrCreateWireGuardDeviceConfig').mockResolvedValue({ config: '[Interface]\nPrivateKey = prodx\n', configSource: 'device' } as any);
  query.mockResolvedValueOnce({ rowCount: 1, rows: [{ user_id:'u1', id:'u1', email:'demo', tariff_id:'t1', tariff_code:'demo', tariff_title:'Demo', preferred_country:'DE' }] })
    .mockResolvedValueOnce({ rows: [{ id:'s1', country_code:'DE', city:'Frankfurt', ip:'31.59.45.197', load_percent:20, type:'wireguard', priority:3, port:51821, health_status:'healthy' }] });
  const r = await post('/api/v1/config/resolve-token', { token:'demo-token', device_id:'android-123' });
  const body = await r.json();
  expect(body.servers[0].protocols[0].config_source).toBe('device');
  expect(body.servers[0].protocols[0].config).toContain('PrivateKey = prodx');
});
it('config_source=device only when config is non-empty', async () => {
  vi.spyOn(provisioning, 'isProvisioningEnabled').mockReturnValue(true);
  vi.spyOn(provisioning, 'getOrCreateWireGuardDeviceConfig').mockResolvedValue({ config: ' [Interface]\nPrivateKey = prod\n', configSource: 'device' } as any);
  query.mockResolvedValueOnce({ rowCount: 1, rows: [{ user_id:'u1', id:'u1', email:'demo', tariff_id:'t1', tariff_code:'demo', tariff_title:'Demo', preferred_country:'DE' }] })
    .mockResolvedValueOnce({ rows: [{ id:'s1', country_code:'DE', city:'Frankfurt', ip:'31.59.45.197', load_percent:20, type:'wireguard', priority:3, port:51821, health_status:'healthy' }] });
  const r = await post('/api/v1/config/resolve-token', { token:'demo-token', device_id:'android-1' });
  const body = await r.json();
  expect(body.servers[0].protocols[0].config_source).toBe('device');
  expect(body.servers[0].protocols[0].config).toContain('PrivateKey = prod');
});

it('resolve-token returns non-empty xray_vless_reality config when Reality env is complete', async () => {
  process.env.XRAY_REALITY_ENABLED = 'true';
  process.env.XRAY_REALITY_HOST = 'vpn.example.com';
  process.env.XRAY_REALITY_PUBLIC_KEY = 'public-key';
  process.env.XRAY_REALITY_SHORT_ID = 'a1b2c3d4';
  process.env.XRAY_REALITY_SERVER_NAME = 'www.cloudflare.com';
  process.env.XRAY_REALITY_UUID = '11111111-1111-4111-8111-111111111111';
  query.mockResolvedValueOnce({ rowCount: 1, rows: [{ user_id:'22222222-2222-4222-8222-222222222222', id:'u1', email:'demo', tariff_id:'t1', tariff_code:'demo', tariff_title:'Demo', preferred_country:'DE' }] })
    .mockResolvedValueOnce({ rows: [{ id:'s1', country_code:'DE', city:'Frankfurt', ip:'31.59.45.197', load_percent:20, type:'xray_vless_reality', priority:2, port:443, health_status:'healthy' }] });

  const r = await post('/api/v1/config/resolve-token', { token:'demo-token', device_id:'android-123' });
  expect(r.status).toBe(200);
  const body = await r.json();
  const protocol = body.servers[0].protocols[0];
  expect(protocol.type).toBe('xray_vless_reality');
  expect(protocol.port).toBe(443);
  expect(protocol.config_source).toBe('xray_reality_env');
  expect(protocol.config).toContain('"host":"vpn.example.com"');
  expect(protocol.config).toContain('"port":443');
  expect(protocol.config).toContain('"uuid":"11111111-1111-4111-8111-111111111111"');
  expect(protocol.config).toContain('"publicKey":"public-key"');
  expect(protocol.config).toContain('"shortId":"a1b2c3d4"');
  expect(protocol.config).toContain('"serverName":"www.cloudflare.com"');
});

it('resolve-token does not expose xray_vless_reality as configured when Reality env is incomplete', async () => {
  delete process.env.XRAY_REALITY_HOST;
  delete process.env.XRAY_REALITY_SHORT_ID;
  delete process.env.XRAY_REALITY_SERVER_NAME;
  delete process.env.XRAY_REALITY_SNI;
  delete process.env.XRAY_REALITY_UUID;
  process.env.XRAY_REALITY_ENABLED = 'true';
  process.env.XRAY_REALITY_PUBLIC_KEY = 'public-key';
  query.mockResolvedValueOnce({ rowCount: 1, rows: [{ user_id:'u1', id:'u1', email:'demo', tariff_id:'t1', tariff_code:'demo', tariff_title:'Demo', preferred_country:'DE' }] })
    .mockResolvedValueOnce({ rows: [{ id:'s1', country_code:'DE', city:'Frankfurt', ip:'31.59.45.197', load_percent:20, type:'xray_vless_reality', priority:2, port:443, health_status:'healthy' }] });

  const r = await post('/api/v1/config/resolve-token', { token:'demo-token' });
  expect(r.status).toBe(200);
  const body = await r.json();
  expect(body.servers[0].protocols[0].config).toBeNull();
  expect(body.servers[0].protocols[0].config_source).toBeNull();
});
