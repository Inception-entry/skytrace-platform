import { Controller, Get, Res } from '@nestjs/common'
import { Response } from 'express'
import { register } from 'prom-client'

@Controller()
export class MetricsController {
  @Get('metrics')
  async metrics(@Res() res: Response) {
    res.set('Content-Type', register.contentType)
    res.end(await register.metrics())
  }
}
