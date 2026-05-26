import { app } from './app';
import { env } from './config/env';

app.listen(env.PORT, () => {
  console.log(`zooot-backend-api listening on :${env.PORT}`);
});
