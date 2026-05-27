# WireGuard MVP (single VPS)

Этот документ описывает первый production-like setup для Zooot MVP на одном VPS.

## 1) Установка

```bash
cd /opt/zooot/server-agent/wireguard
chmod +x install-wireguard.sh generate-client.sh status.sh uninstall-wireguard.sh
./install-wireguard.sh
```

Скрипт:
- проверяет root;
- устанавливает `wireguard`, `iptables`, `qrencode`, `curl`;
- включает `net.ipv4.ip_forward=1`;
- создает `/etc/zooot/wireguard`;
- генерирует серверные ключи на сервере;
- создает `/etc/wireguard/wg0.conf`;
- использует endpoint `31.59.45.197`, порт `51821`, сеть `10.66.66.0/24`, адрес сервера `10.66.66.1/24`;
- включает NAT и стартует `wg-quick@wg0`.

## 2) Firewall

```bash
ufw allow 51821/udp
ufw status
```

## 3) Генерация demo клиента

```bash
./generate-client.sh demo
```

- Конфиг клиента сохраняется в `/etc/zooot/wireguard/clients/demo.conf`.
- Конфиг также печатается в stdout для ручной проверки/импорта.

## 4) Проверка статуса

```bash
./status.sh
```

Команда показывает:
- `wg show`
- `systemctl status wg-quick@wg0 --no-pager`
- слушается ли UDP `51821`

## 5) Secrets policy

- Никогда не коммитьте приватные ключи в git.
- Ключи и клиентские конфиги должны генерироваться только на VPS.
- Любые generated артефакты должны храниться вне репозитория.

## 6) Backend integration (MVP)

Чтобы backend возвращал demo WireGuard config в `POST /api/v1/config/resolve-token`, добавьте override в compose:

```yaml
services:
  backend-api:
    environment:
      WIREGUARD_CLIENT_CONFIG_PATH: /app/runtime/wireguard/clients/demo.conf
    volumes:
      - /etc/zooot/wireguard/clients:/app/runtime/wireguard/clients:ro
```

После изменения на VPS:

```bash
cd /opt/zooot/infra
docker compose up -d --build backend-api
```

Проверка:

```bash
curl -X POST http://31.59.45.197:8080/api/v1/config/resolve-token \
  -H "Content-Type: application/json" \
  -d '{"token":"demo-token"}'
```

Ожидание: у protocol с `type=wireguard` есть поле `config`.

## 7) SECURITY TODO (post-MVP)

- Demo config допустим только для MVP.
- В production нужен уникальный config per user/device/subscription.
- Нужен revoke peer lifecycle.
- Нужна key rotation.
- Нельзя переиспользовать один WireGuard private key для всех пользователей.

## Android MVP validation
- Ensure backend response includes `wireguard` protocol and `config` payload.
- Android client requests VPN permission at connect time (not at config load time).
- After permission approval, tunnel should go UP and `wg show` should display latest handshake + transfer bytes.

## Provisioning + revoke
Use backend env `WIREGUARD_PROVISIONING_ENABLED=true` with mounted clients/scripts paths. For revoke run `server-agent/wireguard/revoke-client.sh <client-name>`.
