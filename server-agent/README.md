# Server Agent

Bash/systemd агент для установки протоколов, health-check и отправки метрик нагрузки.

## Run
```bash
cp .env.example .env
bash install.sh
bash health-report.sh
```

## WireGuard MVP on VPS

> В этом PR добавлены только server-side скрипты для первого реального WireGuard протокола.

```bash
cd /opt/zooot/server-agent/wireguard
chmod +x install-wireguard.sh generate-client.sh status.sh uninstall-wireguard.sh
./install-wireguard.sh
./generate-client.sh demo
./status.sh
```

### Firewall

```bash
ufw allow 51821/udp
ufw status
```

### Что делают скрипты

- `install-wireguard.sh` — ставит WireGuard и поднимает `wg0` на `31.59.45.197:51821`, сеть `10.66.66.0/24`.
- `generate-client.sh <name>` — создает peer, выделяет IP `10.66.66.x/32`, пишет конфиг в `/etc/zooot/wireguard/clients/<name>.conf` и печатает его в stdout.
- `status.sh` — показывает `wg show`, `systemctl status wg-quick@wg0 --no-pager`, и проверку UDP порта `51821`.
- `uninstall-wireguard.sh` — останавливает/отключает сервис и спрашивает подтверждение перед удалением клиентских конфигов.

### Secrets

- Приватные ключи генерируются только на сервере.
- Не коммитьте `/etc/zooot/wireguard/*` и любые сгенерированные `*.private`/`*.key` в git.
