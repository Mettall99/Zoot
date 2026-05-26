import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { pool } from './pool';

async function run() {
  const dir = join(process.cwd(), 'migrations');
  const files = readdirSync(dir).filter((f) => f.endsWith('.sql')).sort();
  for (const file of files) {
    const sql = readFileSync(join(dir, file), 'utf8');
    await pool.query(sql);
    console.log(`applied ${file}`);
  }
  await pool.end();
}

run().catch(async (e) => {
  console.error(e);
  await pool.end();
  process.exit(1);
});
