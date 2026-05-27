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
