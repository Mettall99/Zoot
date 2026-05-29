export type OutlineAccessKey = {
  id: string;
  name?: string;
  password?: string;
  port?: number;
  method?: string;
  accessUrl: string;
};

const outlineBaseUrl = (): string => {
  const value = process.env.OUTLINE_API_URL?.trim();
  if (!value) throw new Error('OUTLINE_API_URL is not set');
  return value.replace(/\/+$/, '');
};

const checkResponse = async (response: Response, action: string): Promise<void> => {
  if (response.ok) return;
  const body = await response.text().catch(() => '');
  throw new Error(`Outline ${action} failed with HTTP ${response.status}${body ? ': ' + body.slice(0, 160) : ''}`);
};

export class OutlineManagementClient {
  constructor(private readonly baseUrl = outlineBaseUrl()) {}

  async createAccessKey(userId: string): Promise<OutlineAccessKey> {
    const response = await fetch(`${this.baseUrl}/access-keys`, { method: 'POST', headers: { 'content-type': 'application/json' } });
    await checkResponse(response, 'create access key');
    const key = await response.json() as OutlineAccessKey;
    if (!key.id || !key.accessUrl) throw new Error('Outline create access key returned an invalid payload');
    return key;
  }

  async deleteAccessKey(keyId: string): Promise<void> {
    const response = await fetch(`${this.baseUrl}/access-keys/${encodeURIComponent(keyId)}`, { method: 'DELETE' });
    await checkResponse(response, 'delete access key');
  }

  async listAccessKeys(): Promise<OutlineAccessKey[]> {
    const response = await fetch(`${this.baseUrl}/access-keys`);
    await checkResponse(response, 'list access keys');
    const payload = await response.json() as { accessKeys?: OutlineAccessKey[] } | OutlineAccessKey[];
    return Array.isArray(payload) ? payload : payload.accessKeys ?? [];
  }

  async renameAccessKey(keyId: string, name: string): Promise<void> {
    const response = await fetch(`${this.baseUrl}/access-keys/${encodeURIComponent(keyId)}/name`, {
      method: 'PUT',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ name })
    });
    if (response.status === 404 || response.status === 405) {
      // TODO: Some Outline Management API versions may not expose rename; creation remains valid.
      return;
    }
    await checkResponse(response, 'rename access key');
  }
}
