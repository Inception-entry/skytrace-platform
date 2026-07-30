import { SetMetadata } from '@nestjs/common'

export const LOG_METADATA = 'log_metadata'

export interface LogMeta {
  module: string
  action: string
}

export const Log = (module: string, action: string) =>
  SetMetadata<string, LogMeta>(LOG_METADATA, { module, action })
