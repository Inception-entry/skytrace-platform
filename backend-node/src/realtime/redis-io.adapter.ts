import { Logger } from '@nestjs/common';
import { IoAdapter } from '@nestjs/platform-socket.io';
import { createAdapter } from '@socket.io/redis-adapter';
import { createClient, type RedisClientType } from 'redis';
import type { ServerOptions } from 'socket.io';
import type { INestApplication } from '@nestjs/common';

/**
 * 多实例 Socket.IO：通过 Redis pub/sub 同步 emit。
 * 任一 Node 实例消费 Rabbit 后 server.emit，其它实例上的客户端也能收到。
 */
export class RedisIoAdapter extends IoAdapter {
  private readonly logger = new Logger(RedisIoAdapter.name);
  private adapterConstructor: ReturnType<typeof createAdapter> | null = null;
  private pubClient: RedisClientType | null = null;
  private subClient: RedisClientType | null = null;

  constructor(private readonly app: INestApplication) {
    super(app);
  }

  async connectToRedis(): Promise<boolean> {
    if ((process.env.SOCKETIO_REDIS_ADAPTER ?? 'true') === 'false') {
      this.logger.log('SOCKETIO_REDIS_ADAPTER=false; using in-memory adapter');
      return false;
    }

    const host = process.env.REDIS_HOST ?? 'localhost';
    const port = process.env.REDIS_PORT ?? '6379';
    const url = `redis://${host}:${port}`;

    try {
      this.pubClient = createClient({ url });
      this.subClient = this.pubClient.duplicate();
      this.pubClient.on('error', (error) => {
        this.logger.warn(`redis pub client error: ${String(error)}`);
      });
      this.subClient.on('error', (error) => {
        this.logger.warn(`redis sub client error: ${String(error)}`);
      });
      await Promise.all([this.pubClient.connect(), this.subClient.connect()]);
      this.adapterConstructor = createAdapter(this.pubClient, this.subClient);
      this.logger.log(`Socket.IO Redis adapter connected (${url})`);
      return true;
    } catch (error) {
      this.logger.warn(
        `Socket.IO Redis adapter unavailable, fallback to memory: ${String(error)}`,
      );
      await this.closeClients();
      this.adapterConstructor = null;
      return false;
    }
  }

  createIOServer(port: number, options?: ServerOptions) {
    const server = super.createIOServer(port, options);
    if (this.adapterConstructor) {
      server.adapter(this.adapterConstructor);
    }
    return server;
  }

  async close(): Promise<void> {
    await this.closeClients();
  }

  private async closeClients(): Promise<void> {
    try {
      await this.subClient?.quit();
    } catch {
      // ignore shutdown races
    }
    try {
      await this.pubClient?.quit();
    } catch {
      // ignore shutdown races
    }
    this.subClient = null;
    this.pubClient = null;
  }
}
