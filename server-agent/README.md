# Server Agent

Bash/systemd агент для установки протоколов, health-check и WireGuard provisioning API на host VPS.

## Run
```bash
cp .env.example .env
bash install.sh
bash health-report.sh
```

## WireGuard scripts on VPS

```bash
cd /opt/zooot/server-agent/wireguard
chmod +x install-wireguard.sh generate-client.sh revoke-client.sh status.sh uninstall-wireguard.sh
./install-wireguard.sh
./generate-client.sh demo --json
./status.sh
```

## Host-side HTTP API

API lives in `server-agent/api/server.js` and must run on host (not in backend container).

```bash
cd /opt/zooot/server-agent/api
ZOOOT_AGENT_TOKEN=change-me ZOOOT_AGENT_HOST=127.0.0.1 ZOOOT_AGENT_PORT=9090 node server.js
curl http://127.0.0.1:9090/health
```

systemd unit: `server-agent/systemd/zooot-server-agent.service`

```bash
sudo cp /opt/zooot/server-agent/systemd/zooot-server-agent.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now zooot-server-agent
curl http://127.0.0.1:9090/health
```

### API auth

- Header: `X-Zooot-Agent-Token`
- Env token: `ZOOOT_AGENT_TOKEN`
- Bind defaults: `127.0.0.1:9090`

### Secrets

- Не коммитьте `/etc/zooot/wireguard/*` и любые сгенерированные `*.private`/`*.key` в git.
- Приватные клиентские конфиги: `chmod 600`.
