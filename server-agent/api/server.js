#!/usr/bin/env node
const http = require('node:http');
const fs = require('node:fs/promises');
const { execFile } = require('node:child_process');
const { promisify } = require('node:util');
const execFileAsync = promisify(execFile);
const MAX_BODY_BYTES = 8192;
const MAX_CONFIG_BYTES = 64 * 1024;
const CLIENT_NAME_REGEX = /^[a-zA-Z0-9._-]{1,100}$/;
const token = (process.env.ZOOOT_AGENT_TOKEN || '').trim();
const host = (process.env.ZOOOT_AGENT_HOST || '127.0.0.1').trim();
const port = Number(process.env.ZOOOT_AGENT_PORT || '9090');
const generateScript = (process.env.ZOOOT_GENERATE_CLIENT_SCRIPT || '/opt/zooot/server-agent/wireguard/generate-client.sh').trim();
const revokeScript = (process.env.ZOOOT_REVOKE_CLIENT_SCRIPT || '/opt/zooot/server-agent/wireguard/revoke-client.sh').trim();
if (!token) { console.error('ZOOOT_AGENT_TOKEN is required'); process.exit(1); }
const json = (res, status, body) => { res.writeHead(status, { 'content-type': 'application/json' }); res.end(JSON.stringify(body)); };
const parseBody = (req) => new Promise((resolve, reject) => { let size = 0; let data = ''; req.on('data', (chunk) => { size += chunk.length; if (size > MAX_BODY_BYTES) { reject(new Error('BODY_TOO_LARGE')); req.destroy(); return; } data += chunk; }); req.on('end', () => { if (!data) return resolve({}); try { resolve(JSON.parse(data)); } catch { reject(new Error('INVALID_JSON')); } }); req.on('error', reject); });
const validateClientName = (name) => typeof name === 'string' && CLIENT_NAME_REGEX.test(name);
const readConfig = async (configPath) => { const stat = await fs.stat(configPath); if (!stat.isFile() || stat.size > MAX_CONFIG_BYTES) throw new Error('INVALID_CONFIG_PATH'); return fs.readFile(configPath, 'utf8'); };
const server = http.createServer(async (req, res) => {
  if (req.method === 'GET' && req.url === '/health') return json(res, 200, { ok: true, service: 'zooot-server-agent' });
  if (req.headers['x-zooot-agent-token'] !== token) return json(res, 401, { ok: false, error: 'UNAUTHORIZED' });
  if (req.method === 'POST' && req.url === '/wireguard/provision') {
    try {
      const body = await parseBody(req);
      if (!validateClientName(body.client_name)) return json(res, 400, { ok: false, error: 'INVALID_CLIENT_NAME' });
      const { stdout } = await execFileAsync(generateScript, [body.client_name, '--json'], { timeout: 10_000 });
      const jsonLine = stdout.trim().split('\n').reverse().find((line) => line.trim().startsWith('{'));
      if (!jsonLine) throw new Error('generate-client did not return JSON');
      const meta = JSON.parse(jsonLine);
      const config = await readConfig(meta.config_path);
      return json(res, 200, { ok: true, client_name: meta.client_name, assigned_ip: meta.assigned_ip, public_key: meta.public_key, config, config_path: meta.config_path });
    } catch (error) {
      const maskSecrets = (value) => String(value || '').replace(/PrivateKey\s*=\s*[^\n]*/gi, 'PrivateKey = [MASKED]').slice(0, 1000);
      console.error('provision_failed', {
        message: error?.message,
        code: error?.code,
        signal: error?.signal,
        stderr: String(error?.stderr || '').slice(0, 1000),
        stdout: maskSecrets(error?.stdout),
      });
      return json(res, 500, { ok: false, error: 'PROVISION_FAILED' });
    }
  }
  if (req.method === 'POST' && req.url === '/wireguard/revoke') {
    try { const body = await parseBody(req); if (!validateClientName(body.client_name)) return json(res, 400, { ok: false, error: 'INVALID_CLIENT_NAME' }); await execFileAsync(revokeScript, [body.client_name], { timeout: 10_000 }); return json(res, 200, { ok: true, client_name: body.client_name, status: 'revoked' }); } catch { return json(res, 500, { ok: false, error: 'REVOKE_FAILED' }); }
  }
  return json(res, 404, { ok: false, error: 'NOT_FOUND' });
});
server.listen(port, host, () => console.log(`zooot-server-agent listening on ${host}:${port}`));
