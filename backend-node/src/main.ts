import { ValidationPipe } from '@nestjs/common';
import { NestFactory } from '@nestjs/core';
import { collectDefaultMetrics } from 'prom-client';
import { AppModule } from './app.module';

async function bootstrap() {
  collectDefaultMetrics({ prefix: 'skytrace_node_' });

  const app = await NestFactory.create(AppModule);
  app.setGlobalPrefix('api', { exclude: ['metrics'] });
  app.enableCors({ origin: true, credentials: true });
  app.useGlobalPipes(new ValidationPipe({ whitelist: true, transform: true }));

  const port = Number(process.env.PORT ?? 3000);
  await app.listen(port, '0.0.0.0');
  console.log(`Node BFF started on port ${port}`);
}
bootstrap();
