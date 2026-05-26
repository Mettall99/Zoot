import 'dotenv/config';
import express from 'express';
import cors from 'cors';
import jwt from 'jsonwebtoken';
import { z } from 'zod';

const app = express();
app.use(cors());
app.use(express.json());

const registerSchema = z.object({ email: z.string().email(), password: z.string().min(8) });

app.get('/health', (_req, res) => res.json({ ok: true, service: 'zooot-backend-api' }));

app.post('/api/v1/auth/register', (req, res) => {
  const parsed = registerSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json(parsed.error.flatten());
  return res.status(201).json({ userId: 'u_demo', email: parsed.data.email });
});

app.post('/api/v1/auth/login', (req, res) => {
  const parsed = registerSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json(parsed.error.flatten());
  const accessToken = jwt.sign({ sub: 'u_demo' }, process.env.JWT_SECRET || 'dev', { expiresIn: '15m' });
  const refreshToken = jwt.sign({ sub: 'u_demo' }, process.env.JWT_REFRESH_SECRET || 'dev2', { expiresIn: '7d' });
  return res.json({ accessToken, refreshToken });
});

app.post('/api/v1/config/resolve-token', (req, res) => {
  const token = String(req.body?.token ?? '');
  if (!token) return res.status(400).json({ message: 'token required' });
  return res.json({
    token,
    subscriptionActive: true,
    countries: [{ code: 'NL', title: 'Netherlands' }],
    servers: [{ id: 'srv_nl_1', countryCode: 'NL', loadPercent: 23, status: 'online' }],
    protocols: ['amneziawg', 'xray_vless_reality', 'wireguard']
  });
});

app.get('/api/v1/servers/recommended', (_req, res) => {
  return res.json({ serverId: 'srv_nl_1', protocol: 'amneziawg', reason: 'load+priority' });
});

const port = Number(process.env.PORT || 8080);
app.listen(port, () => {
  console.log(`zooot-backend-api listening on :${port}`);
});
