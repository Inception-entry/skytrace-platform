import { SetMetadata } from '@nestjs/common';

export const PUBLIC_ROUTE = 'skytrace.public-route';
export const REQUIRED_ROLES = 'skytrace.required-roles';

export const PublicRoute = () => SetMetadata(PUBLIC_ROUTE, true);

export const Roles = (...roles: Array<'ADMIN' | 'OPERATOR' | 'VIEWER'>) =>
  SetMetadata(REQUIRED_ROLES, roles);
