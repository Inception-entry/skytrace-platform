import { Module } from '@nestjs/common';
import { APP_GUARD } from '@nestjs/core';
import { KeycloakJwtService } from './keycloak-jwt.service';
import { HttpJwtAuthGuard } from './http-jwt-auth.guard';
import { HttpRolesGuard } from './http-roles.guard';

@Module({
  providers: [
    KeycloakJwtService,
    {
      provide: APP_GUARD,
      useClass: HttpJwtAuthGuard,
    },
    {
      provide: APP_GUARD,
      useClass: HttpRolesGuard,
    },
  ],
  exports: [KeycloakJwtService],
})
export class AuthModule {}
