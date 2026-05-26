CREATE TABLE IF NOT EXISTS server_protocols (
  id UUID PRIMARY KEY,
  server_id UUID NOT NULL REFERENCES servers(id) ON DELETE CASCADE,
  protocol_id UUID NOT NULL REFERENCES protocols(id) ON DELETE CASCADE,
  port INT NOT NULL,
  status TEXT NOT NULL DEFAULT 'enabled',
  config_template TEXT,
  public_params JSONB NOT NULL DEFAULT '{}'::jsonb,
  health_status TEXT NOT NULL DEFAULT 'unknown',
  last_health_check_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS devices (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  device_name TEXT NOT NULL,
  platform TEXT NOT NULL,
  app_version TEXT,
  device_public_key TEXT,
  last_seen_at TIMESTAMPTZ,
  status TEXT NOT NULL DEFAULT 'active'
);

CREATE TABLE IF NOT EXISTS config_tokens (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token TEXT UNIQUE NOT NULL,
  reusable BOOLEAN NOT NULL DEFAULT FALSE,
  expires_at TIMESTAMPTZ NOT NULL,
  used_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS connection_sessions (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  device_id UUID REFERENCES devices(id) ON DELETE SET NULL,
  server_id UUID REFERENCES servers(id) ON DELETE SET NULL,
  protocol_type TEXT NOT NULL,
  started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  ended_at TIMESTAMPTZ,
  status TEXT NOT NULL DEFAULT 'active'
);
