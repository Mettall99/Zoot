import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { app } from '../src/main.js';

let base = '';
let server: any;

beforeAll(async () => {
  await new Promise<void>((resolve) => {
    server = app.listen(0, () => {
      base = `http://127.0.0.1:${server.address().port}`;
      resolve();
    });
  });
});

afterAll(() => server.close());

describe('health', () => {
  it('works without db usage', async () => {
    const res = await fetch(`${base}/health`);
    expect(res.status).toBe(200);
  });
});
