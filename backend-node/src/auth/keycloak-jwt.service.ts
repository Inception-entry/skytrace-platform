import { Injectable, Logger } from '@nestjs/common';
import {
  createPublicKey,
  verify as verifySignature,
  type KeyObject,
} from 'node:crypto';

const BUSINESS_ROLES = new Set(['ADMIN', 'OPERATOR', 'VIEWER']);
const CLOCK_TOLERANCE_SECONDS = 5;
const DEFAULT_JWKS_CACHE_MILLISECONDS = 5 * 60 * 1000;
const DEFAULT_UNKNOWN_KID_TTL_MS = 30_000;
const DEFAULT_REFRESH_COOLDOWN_MS = 10_000;
const MAX_KID_LENGTH = 128;
const MAX_JWKS_BYTES = 64 * 1024;
const MAX_JWKS_KEYS = 16;
const MAX_UNKNOWN_KID_ENTRIES = 1024;

interface JwtHeader {
  alg?: unknown;
  kid?: unknown;
}

interface JwtPayload {
  sub?: unknown;
  iss?: unknown;
  aud?: unknown;
  exp?: unknown;
  nbf?: unknown;
  preferred_username?: unknown;
  realm_access?: {
    roles?: unknown;
  };
  resource_access?: Record<string, {
    roles?: unknown;
  }>;
}

interface JsonWebKeyWithMetadata {
  kty?: string;
  kid?: string;
  alg?: string;
  use?: string;
  n?: string;
  e?: string;
  [key: string]: unknown;
}

interface JsonWebKeySet {
  keys?: JsonWebKeyWithMetadata[];
}

interface CachedSigningKey {
  key: KeyObject;
  expiresAt: number;
}

export interface RealtimeUser {
  subject: string;
  username: string;
  roles: string[];
  expiresAt: number;
}

export class RealtimeAuthorizationError extends Error {
  constructor(
    readonly status: 401 | 403 | 503,
    readonly code: string,
    message: string,
  ) {
    super(message);
  }
}

function envPositiveInt(name: string, fallback: number): number {
  const raw = process.env[name];
  if (!raw) {
    return fallback;
  }
  const value = Number.parseInt(raw, 10);
  return Number.isInteger(value) && value > 0 ? value : fallback;
}

@Injectable()
export class KeycloakJwtService {
  private readonly logger = new Logger(KeycloakJwtService.name);
  private readonly jwksUri = process.env.AUTH_JWT_JWK_SET_URI
    ?? 'http://localhost:8180/realms/skytrace/protocol/openid-connect/certs';
  private readonly issuer = process.env.AUTH_JWT_ISSUER_URI
    ?? 'http://localhost:8180/realms/skytrace';
  private readonly audience = process.env.AUTH_JWT_AUDIENCE ?? 'skytrace-web';
  private readonly cacheTtlMs = envPositiveInt(
    'AUTH_JWKS_CACHE_MS',
    DEFAULT_JWKS_CACHE_MILLISECONDS,
  );
  private readonly unknownKidTtlMs = envPositiveInt(
    'AUTH_JWKS_UNKNOWN_TTL_MS',
    DEFAULT_UNKNOWN_KID_TTL_MS,
  );
  private readonly refreshCooldownMs = envPositiveInt(
    'AUTH_JWKS_REFRESH_COOLDOWN_MS',
    DEFAULT_REFRESH_COOLDOWN_MS,
  );
  private signingKeys = new Map<string, CachedSigningKey>();
  private readonly unknownKidUntil = new Map<string, number>();
  private nextRefreshAt = 0;
  private refreshPromise: Promise<void> | null = null;

  async verifyAccessToken(token: string): Promise<RealtimeUser> {
    if (!token || token.length > 16_384) {
      throw this.unauthorized('访问令牌缺失或格式无效');
    }

    const parts = token.split('.');
    if (parts.length !== 3) {
      throw this.unauthorized('访问令牌格式无效');
    }

    const header = this.decodePart<JwtHeader>(parts[0]);
    const payload = this.decodePart<JwtPayload>(parts[1]);
    if (header.alg !== 'RS256' || typeof header.kid !== 'string'
      || header.kid.length === 0 || header.kid.length > MAX_KID_LENGTH) {
      throw this.unauthorized('访问令牌签名算法无效');
    }

    const key = await this.getSigningKey(header.kid);
    const validSignature = verifySignature(
      'RSA-SHA256',
      Buffer.from(`${parts[0]}.${parts[1]}`),
      key,
      Buffer.from(parts[2], 'base64url'),
    );
    if (!validSignature) {
      throw this.unauthorized('访问令牌签名无效');
    }

    const now = Math.floor(Date.now() / 1000);
    if (payload.iss !== this.issuer) {
      throw this.unauthorized('访问令牌签发者无效');
    }
    if (!this.hasAudience(payload.aud)) {
      throw this.unauthorized('访问令牌 audience 无效');
    }
    if (typeof payload.exp !== 'number'
      || payload.exp <= now - CLOCK_TOLERANCE_SECONDS) {
      throw this.unauthorized('访问令牌已过期');
    }
    if (typeof payload.nbf === 'number'
      && payload.nbf > now + CLOCK_TOLERANCE_SECONDS) {
      throw this.unauthorized('访问令牌尚未生效');
    }
    if (typeof payload.sub !== 'string' || !payload.sub) {
      throw this.unauthorized('访问令牌缺少用户标识');
    }

    const roles = this.resolveRoles(payload);
    if (!roles.some((role) => BUSINESS_ROLES.has(role))) {
      throw new RealtimeAuthorizationError(
        403,
        'FORBIDDEN',
        '当前用户没有平台访问权限',
      );
    }

    return {
      subject: payload.sub,
      username: typeof payload.preferred_username === 'string'
        ? payload.preferred_username
        : payload.sub,
      roles,
      expiresAt: payload.exp * 1000,
    };
  }

  private async getSigningKey(kid: string): Promise<KeyObject> {
    const now = Date.now();
    const cached = this.signingKeys.get(kid);
    if (cached && cached.expiresAt > now) {
      return cached.key;
    }
    if (cached && now < this.nextRefreshAt) {
      return cached.key;
    }

    const unknownUntil = this.unknownKidUntil.get(kid);
    if (unknownUntil && unknownUntil > now) {
      throw this.unauthorized('找不到访问令牌对应的签名公钥');
    }

    if (now < this.nextRefreshAt) {
      this.rememberUnknownKid(kid, now);
      throw this.unauthorized('找不到访问令牌对应的签名公钥');
    }

    this.nextRefreshAt = now + this.refreshCooldownMs;
    await this.refreshSigningKeys();

    const refreshed = this.signingKeys.get(kid);
    if (!refreshed) {
      this.rememberUnknownKid(kid);
      throw this.unauthorized('找不到访问令牌对应的签名公钥');
    }
    this.unknownKidUntil.delete(kid);
    return refreshed.key;
  }

  private rememberUnknownKid(kid: string, now = Date.now()): void {
    for (const [knownKid, until] of this.unknownKidUntil) {
      if (until <= now) {
        this.unknownKidUntil.delete(knownKid);
      }
    }
    if (this.unknownKidUntil.size >= MAX_UNKNOWN_KID_ENTRIES) {
      this.unknownKidUntil.clear();
    }
    this.unknownKidUntil.set(kid, now + this.unknownKidTtlMs);
  }

  private async refreshSigningKeys(): Promise<void> {
    if (!this.refreshPromise) {
      this.refreshPromise = this.fetchSigningKeys()
        .finally(() => {
          this.refreshPromise = null;
        });
    }
    return this.refreshPromise;
  }

  private async fetchSigningKeys(): Promise<void> {
    try {
      const response = await fetch(this.jwksUri, {
        signal: AbortSignal.timeout(5_000),
      });
      if (!response.ok) {
        throw new Error(`JWKS HTTP ${response.status}`);
      }
      const contentLength = response.headers.get('content-length');
      if (contentLength && Number(contentLength) > MAX_JWKS_BYTES) {
        throw new Error('JWKS 响应过大');
      }
      const body = Buffer.from(await response.arrayBuffer());
      if (body.byteLength > MAX_JWKS_BYTES) {
        throw new Error('JWKS 响应过大');
      }

      const keySet = JSON.parse(body.toString('utf8')) as JsonWebKeySet;
      if (!keySet || !Array.isArray(keySet.keys)) {
        throw new Error('JWKS 格式无效');
      }
      if (keySet.keys.length > MAX_JWKS_KEYS) {
        throw new Error('JWKS key 数量超过限制');
      }

      const nextExpiry = Date.now() + this.cacheTtlMs;
      const nextKeys = new Map<string, CachedSigningKey>();
      for (const jwk of keySet.keys) {
        if (!jwk.kid || jwk.kid.length > MAX_KID_LENGTH || jwk.kty !== 'RSA') {
          continue;
        }
        if (jwk.alg && jwk.alg !== 'RS256') {
          continue;
        }
        if (jwk.use && jwk.use !== 'sig') {
          continue;
        }
        try {
          nextKeys.set(jwk.kid, {
            key: createPublicKey({ key: jwk, format: 'jwk' }),
            expiresAt: nextExpiry,
          });
        } catch (error) {
          this.logger.warn(
            `跳过无效 JWK kid=${jwk.kid}: ${
              error instanceof Error ? error.message : String(error)
            }`,
          );
        }
      }

      if (nextKeys.size === 0) {
        throw new Error('JWKS 中没有可用的 RSA 签名公钥');
      }
      this.signingKeys = nextKeys;
    } catch (error) {
      this.logger.error(
        '无法读取 Keycloak JWKS',
        error instanceof Error ? error.stack : String(error),
      );
      throw new RealtimeAuthorizationError(
        503,
        'AUTH_SERVICE_UNAVAILABLE',
        '身份认证服务暂时不可用',
      );
    }
  }

  private decodePart<T>(value: string): T {
    try {
      const decoded: unknown = JSON.parse(
        Buffer.from(value, 'base64url').toString('utf8'),
      );
      if (!decoded || typeof decoded !== 'object' || Array.isArray(decoded)) {
        throw new Error('JWT part is not an object');
      }
      return decoded as T;
    } catch {
      throw this.unauthorized('访问令牌格式无效');
    }
  }

  private hasAudience(audience: unknown): boolean {
    return audience === this.audience
      || (Array.isArray(audience) && audience.includes(this.audience));
  }

  private resolveRoles(payload: JwtPayload): string[] {
    const realmRoles = this.stringArray(payload.realm_access?.roles);
    const clientRoles = this.stringArray(
      payload.resource_access?.[this.audience]?.roles,
    );
    return [...new Set([...realmRoles, ...clientRoles])]
      .map((role) => role.toUpperCase())
      .sort();
  }

  private stringArray(value: unknown): string[] {
    return Array.isArray(value)
      ? value.filter((item): item is string => typeof item === 'string')
      : [];
  }

  private unauthorized(message: string): RealtimeAuthorizationError {
    return new RealtimeAuthorizationError(401, 'UNAUTHORIZED', message);
  }
}
