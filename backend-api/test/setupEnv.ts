process.env.NODE_ENV = 'test';
process.env.PORT = '3000';
process.env.JWT_SECRET = 'test-jwt-secret-at-least-32-characters';
process.env.JWT_REFRESH_SECRET = 'test-refresh-secret-at-least-32-characters';
process.env.DATABASE_URL = 'postgres://test:test@localhost:5432/test';
process.env.REDIS_URL = 'redis://localhost:6379';
