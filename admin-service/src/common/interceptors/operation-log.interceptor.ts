import {
  Injectable,
  NestInterceptor,
  ExecutionContext,
  CallHandler,
  HttpException,
} from '@nestjs/common'
import { Reflector } from '@nestjs/core'
import { Observable } from 'rxjs'
import { tap } from 'rxjs/operators'
import type { Request, Response } from 'express'
import { LOG_METADATA, LogMeta } from '../decorators/log.decorator'
import { LogsService } from '../../logs/logs.service'
import { serializeRedacted } from '../utils/redact'

type AuthedRequest = Request & { user?: { id: number; username: string } }

function statusFromError(error: unknown): number {
  if (error instanceof HttpException) {
    return error.getStatus()
  }
  return 500
}

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
    const req = context.switchToHttp().getRequest<AuthedRequest>()

    return next.handle().pipe(
      tap({
        next: () => {
          const res = context.switchToHttp().getResponse<Response>()
          this.writeLog(req, meta, start, res.statusCode)
        },
        error: (error: unknown) => {
          this.writeLog(req, meta, start, statusFromError(error))
        },
      }),
    )
  }

  private writeLog(
    req: AuthedRequest,
    meta: LogMeta,
    start: number,
    status: number,
  ) {
    void this.logsService.record({
      userId: req.user?.id ?? 0,
      username: req.user?.username ?? 'anonymous',
      module: meta.module,
      action: meta.action,
      method: req.method,
      path: req.path,
      params: serializeRedacted(req.body),
      ip: req.ip ?? '',
      status,
      duration: Date.now() - start,
    })
  }
}
