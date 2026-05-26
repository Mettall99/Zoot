import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import morgan from 'morgan';
import { apiRouter } from './routes/api';
import { healthRouter } from './routes/health';
import { errorHandler, notFound } from './middleware/error';

export const app = express();
app.use(helmet());
app.use(cors());
app.use(express.json());
app.use(morgan('tiny'));
app.use(healthRouter);
app.use(apiRouter);
app.use(notFound);
app.use(errorHandler);
