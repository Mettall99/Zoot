import { beforeAll, afterAll, describe, expect, it, vi, beforeEach } from 'vitest';

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
  beforeEach(() => query.mockReset());
  it('register creates user', async () => { query.mockResolvedValueOnce({ rowCount: 0, rows: [] }).mockResolvedValueOnce({ rows: [{ id: 'u1', email: 'a@b.com' }] }); const r = await post('/api/v1/auth/register',{email:'a@b.com',password:'password123'}); expect(r.status).toBe(201); });
  it('duplicate email returns 409', async () => { query.mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'u1' }] }); const r = await post('/api/v1/auth/register',{email:'a@b.com',password:'password123'}); expect(r.status).toBe(409); });
  it('login returns tokens', async () => { query.mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'u1', password_hash: 'hash' }] }); const r = await post('/api/v1/auth/login',{email:'a@b.com',password:'password123'}); expect(r.status).toBe(200); expect((await r.json()).accessToken).toBeTruthy(); });
  it('invalid login returns 401', async () => { query.mockResolvedValueOnce({ rowCount: 0, rows: [] }); const r = await post('/api/v1/auth/login',{email:'a@b.com',password:'password123'}); expect(r.status).toBe(401); });
  it('resolve demo-token returns servers', async () => { query.mockResolvedValueOnce({ rowCount: 1, rows: [{ id:'u1', email:'demo', tariff_id:'t1', tariff_code:'demo', tariff_title:'Demo', preferred_country:'DE' }] }).mockResolvedValueOnce({ rows: [{ id:'s1', country_code:'DE', city:'Frankfurt', ip:'31.59.45.197', load_percent:20, type:'amneziawg', priority:1, port:51820, health_status:'healthy' }] }); const r=await post('/api/v1/config/resolve-token',{token:'demo-token'}); expect(r.status).toBe(200); });
  it('invalid token returns 404', async () => { query.mockResolvedValueOnce({ rowCount: 0, rows: [] }); const r=await post('/api/v1/config/resolve-token',{token:'x'}); expect(r.status).toBe(404); });
  it('inactive subscription returns 403', async () => { query.mockResolvedValueOnce({ rowCount: 1, rows: [{ id:'u1', email:'demo', tariff_id:null }] }); const r=await post('/api/v1/config/resolve-token',{token:'demo-token'}); expect(r.status).toBe(403); });
  it('health works', async () => { const r = await fetch(`${base}/health`); expect(r.status).toBe(200); });
});
