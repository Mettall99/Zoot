CREATE TABLE IF NOT EXISTS vpn_config_tokens (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id TEXT NOT NULL,
  token_hash TEXT UNIQUE NOT NULL,
  token_prefix TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'revoked', 'expired')),
  expires_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS vpn_protocol_credentials (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id TEXT NOT NULL,
  config_token_id UUID NOT NULL REFERENCES vpn_config_tokens(id) ON DELETE CASCADE,
  protocol TEXT NOT NULL CHECK (protocol IN ('outline_shadowsocks', 'wireguard', 'vless_reality')),
  server_id TEXT NOT NULL,
  external_key_id TEXT,
  encrypted_connect_uri TEXT,
  status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'revoked', 'error')),
  last_error TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_vpn_config_tokens_token_hash ON vpn_config_tokens(token_hash);
CREATE INDEX IF NOT EXISTS idx_vpn_protocol_credentials_config_token ON vpn_protocol_credentials(config_token_id);
