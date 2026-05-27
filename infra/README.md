# Infra

Локальный запуск backend + postgres + redis:

```bash
docker compose up -d
```

## WireGuard provisioning via host agent (Linux/VPS)

Backend container должен ходить в host-side server-agent и не должен устанавливать `wireguard-tools`.

```yaml
backend-api:
  extra_hosts:
    - "host.docker.internal:host-gateway"
  environment:
    WIREGUARD_PROVISIONING_ENABLED: "true"
    WIREGUARD_PROVISIONING_MODE: agent
    WIREGUARD_AGENT_URL: http://host.docker.internal:9090
    WIREGUARD_AGENT_TOKEN: change-me
    WIREGUARD_CLIENT_CONFIG_PATH: /app/runtime/wireguard/clients/demo.conf
  volumes:
    - /etc/zooot/wireguard/clients:/app/runtime/wireguard/clients:ro
```

Если агент недоступен, backend должен возвращаться к `demo_fallback` (при наличии `WIREGUARD_CLIENT_CONFIG_PATH`).
