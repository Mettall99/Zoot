import { beforeAll, afterAll, describe, expect, it, vi, beforeEach } from 'vitest';
import os from 'node:os';
import path from 'node:path';
import fs from 'node:fs/promises';

const query = vi.fn();
vi.mock('../src/db.js', () => ({ getDb: () => ({ query }) }));
vi.mock('argon2', () => ({ default: { hash: vi.fn(async () => 'hash'), verify: vi.fn(async (h: string, p: string) => h === 'hash' && p === 'password123') } }));

import { app } from '../src/main.js';

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
  expect(body.servers[0].protocols[0].config_source).toBe('demo_fallback');
});

it('resolve-token rejects invalid device_id', async () => {
  const r = await post('/api/v1/config/resolve-token', { token:'demo-token', device_id:'bad id with space' });
  expect(r.status).toBe(400);
});
