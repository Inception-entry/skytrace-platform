import { createParamDecorator, ExecutionContext } from '@nestjs/common'

export interface RequestUser {
  id: number
  username: string
}

export const CurrentUser = createParamDecorator(
  (_data: unknown, ctx: ExecutionContext): RequestUser => {
    const request = ctx.switchToHttp().getRequest<{ user: RequestUser }>()
    return request.user
  },
)
