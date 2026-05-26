import { ZodError } from 'zod';

export type ApiErrorCode =
  | 'VALIDATION_ERROR'
  | 'USER_ALREADY_EXISTS'
  | 'INVALID_CREDENTIALS'
  | 'CONFIG_TOKEN_NOT_FOUND'
  | 'SUBSCRIPTION_INACTIVE';

export const apiError = (code: ApiErrorCode, message: string) => ({ error: { code, message } });

export const zodToValidation = (error: ZodError) =>
  apiError('VALIDATION_ERROR', error.issues.map((i) => i.message).join('; '));
