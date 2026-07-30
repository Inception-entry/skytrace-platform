import { Injectable, NestInterceptor, ExecutionContext, CallHandler } from '@nestjs/common'
import { Reflector } from '@nestjs/core'
import { Observable } from 'rxjs'
import { tap } from 'rxjs/operators'
import type { Request, Response } from 'express'
import { LOG_METADATA, LogMeta } from '../decorators/log.decorator'
import { LogsService } from '../../logs/logs.service'

@Injectable()
export class OperationLogInterceptor implements NestInterceptor {
  constructor(
    private readonly reflector: Reflector,
    private readonly logsService: LogsService,
  ) {}

  intercept(context: ExecutionContext, next: CallHandler): Observable<unknown> {
    const meta = this.reflector.get<LogMeta>(LOG_METADATA, context.getHandler())
    if (!meta) return next.handle()

    const start = Date.now()
    const req = context.switchToHttp().getRequest<Request & { user?: { id: number; username: string } }>()

    return next.handle().pipe(
      tap({
        next: () => {
          const res = context.switchToHttp().getResponse<Response>()
          void this.logsService.record({
            userId: req.user?.id ?? 0,
            username: req.user?.username ?? 'anonymous',
            module: meta.module,
            action: meta.action,
            method: req.method,
            path: req.path,
            params: JSON.stringify(req.body ?? {}).slice(0, 500),
            ip: req.ip ?? '',
            status: res.statusCode,
            duration: Date.now() - start,
          })
        },
      }),
    )
  }
}
