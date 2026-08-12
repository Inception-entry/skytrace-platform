import {
  OnGatewayInit,
  MessageBody,
  OnGatewayConnection,
  OnGatewayDisconnect,
  SubscribeMessage,
  WebSocketGateway,
  WebSocketServer,
} from '@nestjs/websockets';
import { Server, Socket } from 'socket.io';
import { Logger } from '@nestjs/common';
import {
  KeycloakJwtService,
  RealtimeAuthorizationError,
  RealtimeUser,
} from '../auth/keycloak-jwt.service';

const allowedOrigins = (process.env.WS_ALLOWED_ORIGIN
  ?? 'http://localhost:8888')
  .split(',')
  .map((origin) => origin.trim())
  .filter(Boolean);

@WebSocketGateway(Number(process.env.WS_PORT ?? 3001), {
  cors: { origin: allowedOrigins, credentials: false },
  serveClient: true,
})
export class AlarmRealtimeGateway implements
  OnGatewayInit,
  OnGatewayConnection,
  OnGatewayDisconnect {
  private readonly logger = new Logger(AlarmRealtimeGateway.name);
  private readonly expirationTimers = new Map<string, NodeJS.Timeout>();

  constructor(private readonly jwtService: KeycloakJwtService) {}

  @WebSocketServer()
  server!: Server;

  afterInit(server: Server) {
    server.use((client, next) => {
      void this.authenticate(client, next);
    });
  }

  handleConnection(client: Socket) {
    const user = client.data.user as RealtimeUser;
    const expiresIn = Math.max(user.expiresAt - Date.now(), 0);
    const timer = setTimeout(
      () => client.disconnect(true),
      Math.min(expiresIn, 2_147_483_647),
    );
    this.expirationTimers.set(client.id, timer);

    client.emit('connected', {
      clientId: client.id,
      message: 'connected to authenticated alarm websocket',
      username: user.username,
      roles: user.roles,
      expiresAt: new Date(user.expiresAt).toISOString(),
    });
    this.logger.log(
      `realtime connected clientId=${client.id} subject=${user.subject}`,
    );
  }

  handleDisconnect(client: Socket) {
    const timer = this.expirationTimers.get(client.id);
    if (timer) {
      clearTimeout(timer);
      this.expirationTimers.delete(client.id);
    }
    this.logger.log(`realtime disconnected clientId=${client.id}`);
  }

  @SubscribeMessage('ping')
  handlePing(@MessageBody() data: unknown) {
    return { event: 'pong', data };
  }

  broadcastAlarm(alarm: unknown) {
    this.server.emit('alarm.created', alarm);
  }

  broadcastDeviceTelemetry(telemetry: unknown) {
    this.server.emit('device.telemetry', telemetry);
  }

  private async authenticate(
    client: Socket,
    next: (error?: Error) => void,
  ) {
    try {
      const token = this.extractToken(client.handshake.auth?.token);
      client.data.user = await this.jwtService.verifyAccessToken(token);
      next();
    } catch (error) {
      const authorizationError = error instanceof RealtimeAuthorizationError
        ? error
        : new RealtimeAuthorizationError(
          401,
          'UNAUTHORIZED',
          '实时连接身份认证失败',
        );
      const connectionError = new Error(authorizationError.message) as Error & {
        data?: Record<string, unknown>;
      };
      connectionError.data = {
        success: false,
        status: authorizationError.status,
        code: authorizationError.code,
        message: authorizationError.message,
      };
      next(connectionError);
    }
  }

  private extractToken(value: unknown): string {
    if (typeof value !== 'string') {
      throw new RealtimeAuthorizationError(
        401,
        'UNAUTHORIZED',
        '实时连接缺少访问令牌',
      );
    }
    return value.startsWith('Bearer ') ? value.slice(7) : value;
  }
}
