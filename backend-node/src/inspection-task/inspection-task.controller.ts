import {
  Body,
  Controller,
  Get,
  Param,
  Post,
  Res,
  Put,
} from '@nestjs/common';
import { Roles } from '../auth/http-auth.decorators';
import { JavaClientService } from '../common/java-client/java-client.service';
import { AnalyzeInspectionTaskDto } from './dto/analyze-inspection-task.dto';
import {
  CreateInspectionTaskDto,
  UpdateInspectionTaskDto,
} from './dto/save-inspection-task.dto';

interface StreamResponse extends NodeJS.WritableStream {
  statusCode: number;
  headersSent: boolean;
  writableEnded: boolean;
  setHeader(name: string, value: string): void;
  on(event: 'close', listener: () => void): this;
  once(event: 'finish', listener: () => void): this;
  end(): this;
}

@Controller('inspection-tasks')
export class InspectionTaskController {
  constructor(
    private readonly javaClient: JavaClientService,
  ) {}

  @Get()
  list(): Promise<unknown> {
    return this.javaClient.get('/inspection-tasks');
  }

  @Get(':taskCode')
  detail(@Param('taskCode') taskCode: string): Promise<unknown> {
    return this.javaClient.get(`/inspection-tasks/${taskCode}`);
  }

  @Get(':taskCode/analyses')
  analyses(@Param('taskCode') taskCode: string): Promise<unknown> {
    return this.javaClient.get(
      `/inspection-tasks/${taskCode}/analyses`,
    );
  }

  @Get(':taskCode/telemetry')
  telemetry(@Param('taskCode') taskCode: string): Promise<unknown> {
    return this.javaClient.get(
      `/inspection-tasks/${taskCode}/telemetry`,
    );
  }

  @Post()
  @Roles('ADMIN', 'OPERATOR')
  create(@Body() dto: CreateInspectionTaskDto): Promise<unknown> {
    return this.javaClient.post('/inspection-tasks', dto);
  }

  @Put(':taskCode')
  @Roles('ADMIN', 'OPERATOR')
  update(
    @Param('taskCode') taskCode: string,
    @Body() dto: UpdateInspectionTaskDto,
  ): Promise<unknown> {
    return this.javaClient.put(
      `/inspection-tasks/${taskCode}`,
      dto,
    );
  }

  @Post(':taskCode/start')
  @Roles('ADMIN', 'OPERATOR')
  start(@Param('taskCode') taskCode: string): Promise<unknown> {
    return this.javaClient.post(
      `/inspection-workflows/${taskCode}`,
      {},
    );
  }

  @Post(':taskCode/complete')
  @Roles('ADMIN', 'OPERATOR')
  complete(@Param('taskCode') taskCode: string): Promise<unknown> {
    return this.javaClient.post(
      `/inspection-workflows/${taskCode}/complete`,
      {},
    );
  }

  @Post(':taskCode/cancel')
  @Roles('ADMIN', 'OPERATOR')
  cancel(@Param('taskCode') taskCode: string): Promise<unknown> {
    return this.javaClient.post(
      `/inspection-workflows/${taskCode}/cancel`,
      {},
    );
  }

  @Get(':taskCode/workflow-status')
  workflowStatus(
    @Param('taskCode') taskCode: string,
  ): Promise<unknown> {
    return this.javaClient.get(
      `/inspection-workflows/${taskCode}/status`,
    );
  }

  @Post(':taskCode/analysis')
  @Roles('ADMIN', 'OPERATOR')
  analyze(
    @Param('taskCode') taskCode: string,
    @Body() dto: AnalyzeInspectionTaskDto,
  ): Promise<unknown> {
    return this.javaClient.post(
      `/inspection-workflows/${taskCode}/analysis`,
      dto,
      180_000,
    );
  }

  @Post(':taskCode/analysis/stream')
  @Roles('ADMIN', 'OPERATOR')
  async streamAnalysis(
    @Param('taskCode') taskCode: string,
    @Body() dto: AnalyzeInspectionTaskDto,
    @Res() response: StreamResponse,
  ): Promise<void> {
    const stream = await this.javaClient.postStream(
      `/inspection-workflows/${taskCode}/analysis/stream`,
      dto,
    );

    response.statusCode = 200;
    response.setHeader('Content-Type', 'text/event-stream; charset=utf-8');
    response.setHeader('Cache-Control', 'no-cache, no-transform');
    response.setHeader('X-Accel-Buffering', 'no');

    await new Promise<void>((resolve) => {
      let finished = false;
      const finish = () => {
        if (finished) return;
        finished = true;
        resolve();
      };

      response.once('finish', finish);
      response.on('close', () => {
        if (!response.writableEnded && !stream.destroyed) {
          stream.destroy();
        }
        finish();
      });
      stream.once('error', () => {
        if (!response.writableEnded) {
          const data = JSON.stringify({
            code: 'STREAM_PROXY_INTERRUPTED',
            message: '流式代理连接中断，请重试',
            retryable: true,
          });
          response.write(`event: error\ndata: ${data}\n\n`);
          response.end();
        }
        finish();
      });
      stream.pipe(response);
    });
  }
}
