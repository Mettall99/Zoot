import { z } from 'zod';

const realityEnvSchema = z.object({
  host: z.string().min(1),
  publicKey: z.string().min(1),
  shortId: z.string().min(1),
  serverName: z.string().min(1),
  port: z.number().int().min(1).max(65535).default(443),
  flow: z.string().optional(),
  fingerprint: z.string().min(1).default('chrome')
});

export type XrayRealityClientConfig = z.infer<typeof realityEnvSchema> & {
  public_key: string;
  short_id: string;
  server_name: string;
  protocol: 'xray_vless_reality';
  uuid: string;
  security: 'reality';
  network: 'tcp';
  configVersion: 1;
  uri: string;
};

const envValue = (name: string): string | undefined => {
  const value = process.env[name]?.trim();
  return value ? value : undefined;
};

const isEnabled = (): boolean => ['true', '1', 'yes'].includes((process.env.XRAY_REALITY_ENABLED || '').trim().toLowerCase());

export const getXrayRealityConfig = (params: { userId: string; serverIp: string; port?: number | string | null }): string | null => {
  if (!isEnabled()) return null;

  const parsed = realityEnvSchema.safeParse({
    host: envValue('XRAY_REALITY_HOST') || params.serverIp,
    publicKey: envValue('XRAY_REALITY_PUBLIC_KEY'),
    shortId: envValue('XRAY_REALITY_SHORT_ID'),
    serverName: envValue('XRAY_REALITY_SERVER_NAME') || envValue('XRAY_REALITY_SNI'),
    port: Number(envValue('XRAY_REALITY_PORT') || params.port || 443),
    flow: envValue('XRAY_REALITY_FLOW') || 'xtls-rprx-vision',
    fingerprint: envValue('XRAY_REALITY_FINGERPRINT') || 'chrome'
  });

  if (!parsed.success) return null;

  const uuid = envValue('XRAY_REALITY_UUID') || params.userId;
  const cfg = parsed.data;
  const flowParam = cfg.flow ? `&flow=${encodeURIComponent(cfg.flow)}` : '';
  const uri = `vless://${encodeURIComponent(uuid)}@${encodeURIComponent(cfg.host)}:${cfg.port}?encryption=none&security=reality&sni=${encodeURIComponent(cfg.serverName)}&fp=${encodeURIComponent(cfg.fingerprint)}&pbk=${encodeURIComponent(cfg.publicKey)}&sid=${encodeURIComponent(cfg.shortId)}&type=tcp${flowParam}#Zooot-Reality`;

  const config: XrayRealityClientConfig = {
    protocol: 'xray_vless_reality',
    configVersion: 1,
    host: cfg.host,
    port: cfg.port,
    uuid,
    publicKey: cfg.publicKey,
    public_key: cfg.publicKey,
    shortId: cfg.shortId,
    short_id: cfg.shortId,
    serverName: cfg.serverName,
    server_name: cfg.serverName,
    flow: cfg.flow,
    fingerprint: cfg.fingerprint,
    security: 'reality',
    network: 'tcp',
    uri
  };

  return JSON.stringify(config);
};
