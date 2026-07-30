import { Controller, Get, Header } from '@nestjs/common';
import { register } from 'prom-client';
import { PublicRoute } from '../auth/http-auth.decorators';

@Controller('metrics')
@PublicRoute()
export class MetricsController {
  @Get()
  @Header('Content-Type', 'text/plain; version=0.0.4; charset=utf-8')
  async metrics(): Promise<string> {
    return register.metrics();
  }
}
