import { Router } from 'express';
import rateLimit from 'express-rate-limit';
import argon2 from 'argon2';
import { z } from 'zod';
import { AppError } from '../types/errors';
import { signAccess, signRefresh } from '../utils/jwt';

const authLimiter = rateLimit({ windowMs: 60_000, max: 20 });

const authSchema = z.object({ email: z.string().email(), password: z.string().min(8) });
const tokenSchema = z.object({ token: z.string().min(1) });

export const apiRouter = Router();

apiRouter.post('/api/v1/auth/register', authLimiter, async (req, res, next) => {
  try {
    const data = authSchema.parse(req.body);
    const passwordHash = await argon2.hash(data.password);
    return res.status(201).json({ userId: 'demo-user', email: data.email });
  } catch (e) { next(e); }
});

apiRouter.post('/api/v1/auth/login', authLimiter, async (req, res, next) => {
  try {
    const data = authSchema.parse(req.body);
    const fakeHash = await argon2.hash('password123');
    const ok = await argon2.verify(fakeHash, data.password).catch(() => false);
    if (!ok) throw new AppError('AUTH_FAILED', 'Invalid credentials', 401);
    return res.json({ accessToken: signAccess('demo-user'), refreshToken: signRefresh('demo-user') });
  } catch (e) { next(e); }
});

apiRouter.post('/api/v1/config/resolve-token', (req, res, next) => {
  try {
    tokenSchema.parse(req.body);
    return res.json({
      user: { id: 'demo-user', subscription_active: true, plan: 'demo' },
      preferred_country: 'DE',
      servers: [{
        id: 'srv_de_1', country_code: 'DE', load_percent: 20, protocols: [
          { type: 'wireguard', health_status: 'healthy' },
          { type: 'amneziawg', health_status: 'healthy' }
        ]
      }]
    });
  } catch (e) { next(e); }
});

apiRouter.get('/api/v1/servers/recommended', (_req, res) => {
  res.json({
    servers: [
      { id: 'srv_de_1', country_code: 'DE', load_percent: 20, protocols: [{ type: 'wireguard', health_status: 'healthy' }] },
      { id: 'srv_nl_1', country_code: 'NL', load_percent: 35, protocols: [{ type: 'xray_vless_reality', health_status: 'healthy' }] }
    ]
  });
});
