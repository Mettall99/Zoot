CREATE TABLE IF NOT EXISTS wireguard_devices (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  server_id uuid NOT NULL REFERENCES servers(id) ON DELETE CASCADE,
  device_id text NOT NULL,
  device_name text NULL,
  assigned_ip inet NOT NULL,
  public_key text NOT NULL,
  config_path text NULL,
  status text NOT NULL DEFAULT 'active',
  created_at timestamptz NOT NULL DEFAULT NOW(),
  updated_at timestamptz NOT NULL DEFAULT NOW(),
  revoked_at timestamptz NULL,
  CONSTRAINT wireguard_devices_user_device_server_unique UNIQUE (user_id, device_id, server_id),
  CONSTRAINT wireguard_devices_server_ip_unique UNIQUE (server_id, assigned_ip),
  CONSTRAINT wireguard_devices_public_key_unique UNIQUE (public_key)
);

CREATE INDEX IF NOT EXISTS wireguard_devices_user_id_idx ON wireguard_devices(user_id);
CREATE INDEX IF NOT EXISTS wireguard_devices_status_idx ON wireguard_devices(status);
CREATE INDEX IF NOT EXISTS wireguard_devices_device_id_idx ON wireguard_devices(device_id);
