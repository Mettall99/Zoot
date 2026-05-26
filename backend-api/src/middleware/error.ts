import { NextFunction, Request, Response } from 'express';
import { ZodError } from 'zod';
import { AppError } from '../types/errors';

export function notFound(_req: Request, _res: Response, next: NextFunction) {
  next(new AppError('NOT_FOUND', 'Route not found', 404));
}

export function errorHandler(err: unknown, _req: Request, res: Response, _next: NextFunction) {
  if (err instanceof ZodError) {
    return res.status(400).json({ error: { code: 'VALIDATION_ERROR', message: err.issues.map((x) => x.message).join('; ') } });
  }
  if (err instanceof AppError) {
    return res.status(err.status).json({ error: { code: err.code, message: err.message } });
  }
  return res.status(500).json({ error: { code: 'INTERNAL_ERROR', message: 'Unexpected error' } });
}
