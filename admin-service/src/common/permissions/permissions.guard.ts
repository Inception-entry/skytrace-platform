import {
  CanActivate,
  ExecutionContext,
  ForbiddenException,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common'
import { Reflector } from '@nestjs/core'
import { PERMISSIONS_ANY_KEY, PERMISSIONS_KEY } from '../decorators/require-permissions.decorator'
import { PermissionsService } from '../permissions/permissions.service'
import type { RequestUser } from '../decorators/current-user.decorator'

export interface RequestUserWithPermissions extends RequestUser {
  permissions?: string[]
  roles?: string[]
}

@Injectable()
export class PermissionsGuard implements CanActivate {
  constructor(
    private readonly reflector: Reflector,
    private readonly permissions: PermissionsService,
  ) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const requiredAll = this.reflector.getAllAndOverride<string[]>(PERMISSIONS_KEY, [
      context.getHandler(),
      context.getClass(),
    ])
    const requiredAny = this.reflector.getAllAndOverride<string[]>(PERMISSIONS_ANY_KEY, [
      context.getHandler(),
      context.getClass(),
    ])
    if (!requiredAll?.length && !requiredAny?.length) return true

    const request = context.switchToHttp().getRequest<{ user?: RequestUserWithPermissions }>()
    const user = request.user
    if (!user?.id) throw new UnauthorizedException()

    const [codes, roles] = await Promise.all([
      this.permissions.getPermissionCodes(user.id),
      this.permissions.getRoleCodes(user.id),
    ])
    user.permissions = codes
    user.roles = roles

    if (roles.includes('super_admin')) return true

    if (requiredAll?.length) {
      const missing = requiredAll.filter(code => !codes.includes(code))
      if (missing.length > 0) {
        throw new ForbiddenException(`缺少权限：${missing.join(', ')}`)
      }
    }

    if (requiredAny?.length && !requiredAny.some(code => codes.includes(code))) {
      throw new ForbiddenException(`缺少权限（需其一）：${requiredAny.join(', ')}`)
    }

    return true
  }
}
