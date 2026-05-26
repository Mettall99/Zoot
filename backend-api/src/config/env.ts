import { config } from 'dotenv';
import { z } from 'zod';

config();

const isTest = process.env.NODE_ENV === 'test';

const envSchema = z.object({
  NODE_ENV: z.enum(['development', 'test', 'production']).default('development'),
  PORT: z.coerce.number().int().positive().default(3000),
  JWT_SECRET: z.string().min(8).default('test_jwt_secret_123'),
  JWT_REFRESH_SECRET: z.string().min(8).default('test_refresh_secret_123'),
  DATABASE_URL: z.string().url().default('postgres://zooot:zooot@localhost:5432/zooot'),
  REDIS_URL: z.string().url().default('redis://localhost:6379')
});

const input = isTest
  ? {
      NODE_ENV: 'test',
      PORT: process.env.PORT ?? '3000',
      JWT_SECRET: process.env.JWT_SECRET ?? 'test_jwt_secret_123',
      JWT_REFRESH_SECRET: process.env.JWT_REFRESH_SECRET ?? 'test_refresh_secret_123',
      DATABASE_URL: process.env.DATABASE_URL ?? 'postgres://zooot:zooot@localhost:5432/zooot',
      REDIS_URL: process.env.REDIS_URL ?? 'redis://localhost:6379'
    }
  : process.env;

export const env = envSchema.parse(input);
