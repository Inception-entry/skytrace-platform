import {
  Injectable,
  Logger,
  OnModuleDestroy,
  OnModuleInit,
} from '@nestjs/common';
import * as amqp from 'amqplib';
import { AlarmRealtimeGateway } from '../realtime/alarm-realtime.gateway';

@Injectable()
export class AlarmRealtimeConsumer
  implements OnModuleInit, OnModuleDestroy
{
  private readonly logger = new Logger(AlarmRealtimeConsumer.name);
  private connection: amqp.ChannelModel | null = null;
  private channel: amqp.Channel | null = null;

  constructor(private readonly alarmGateway: AlarmRealtimeGateway) {}

  async onModuleInit(): Promise<void> {
    if ((process.env.MESSAGING_ENABLED ?? 'true') === 'false') {
      this.logger.log('messaging disabled; skip alarm realtime consumer');
      return;
    }

    const host = process.env.RABBITMQ_HOST ?? 'localhost';
    const port = process.env.RABBITMQ_PORT ?? '5672';
    const username = process.env.RABBITMQ_USERNAME ?? 'admin';
    const password = process.env.RABBITMQ_PASSWORD ?? 'admin123';
    const url = `amqp://${encodeURIComponent(username)}:${encodeURIComponent(password)}@${host}:${port}`;

    try {
      this.connection = await amqp.connect(url);
      this.channel = await this.connection.createChannel();
      const exchange = 'skytrace.alarm.realtime';
      const queue = 'skytrace.alarm.realtime.node';
      await this.channel.assertExchange(exchange, 'fanout', { durable: true });
      await this.channel.assertQueue(queue, { durable: true });
      await this.channel.bindQueue(queue, exchange, '');
      await this.channel.consume(queue, (message) => {
        if (!message || !this.channel) {
          return;
        }
        try {
          const payload = JSON.parse(message.content.toString('utf8')) as {
            type?: string;
          };
          if (payload?.type === 'device.telemetry') {
            this.alarmGateway.broadcastDeviceTelemetry(payload);
          } else {
            this.alarmGateway.broadcastAlarm({
              success: true,
              message: 'success',
              data: payload,
            });
          }
          this.channel.ack(message);
        } catch (error) {
          this.logger.warn(
            `failed to broadcast realtime event: ${String(error)}`,
          );
          this.channel.nack(message, false, false);
        }
      });
      this.logger.log('alarm realtime consumer started');
    } catch (error) {
      this.logger.warn(
        `alarm realtime consumer unavailable: ${String(error)}`,
      );
    }
  }

  async onModuleDestroy(): Promise<void> {
    try {
      await this.channel?.close();
      await this.connection?.close();
    } catch {
      // ignore shutdown races
    }
  }
}
