import {
  BadRequestException,
  Body,
  Controller,
  Get,
  Param,
  Post,
  Put,
} from '@nestjs/common';
import { Roles } from '../auth/http-auth.decorators';
import { JavaClientService } from '../shared/java-client.service';

@Controller('routes')
export class RouteController {
  constructor(private readonly javaClient: JavaClientService) {}

  @Get()
  list() {
    return this.javaClient.get('/routes');
  }

  @Get(':routeCode')
  detail(@Param('routeCode') routeCode: string) {
    this.requireRouteCode(routeCode);
    return this.javaClient.get(
      `/routes/${encodeURIComponent(routeCode)}`,
    );
  }

  @Post()
  @Roles('ADMIN', 'OPERATOR')
  create(@Body() body: Record<string, unknown>) {
    return this.javaClient.post('/routes', body);
  }

  @Put(':routeCode')
  @Roles('ADMIN', 'OPERATOR')
  update(
    @Param('routeCode') routeCode: string,
    @Body() body: Record<string, unknown>,
  ) {
    this.requireRouteCode(routeCode);
    return this.javaClient.put(
      `/routes/${encodeURIComponent(routeCode)}`,
      body,
    );
  }

  private requireRouteCode(routeCode: string) {
    if (!routeCode?.trim()) {
      throw new BadRequestException('routeCode 不能为空');
    }
  }
}
