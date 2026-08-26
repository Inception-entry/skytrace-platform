import { Injectable, Logger } from '@nestjs/common';
import {
  createPublicKey,
  verify as verifySignature,
  type KeyObject,
} from 'node:crypto';

const BUSINESS_ROLES = new Set(['ADMIN', 'OPERATOR', 'VIEWER']);
const CLOCK_TOLERANCE_SECONDS = 5;
const JWKS_CACHE_MILLISECONDS = 5 * 60 * 1000;

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

@Injectable()
export class KeycloakJwtService {
  private readonly logger = new Logger(KeycloakJwtService.name);
  private readonly jwksUri = process.env.AUTH_JWT_JWK_SET_URI
    ?? 'http://localhost:8180/realms/skytrace/protocol/openid-connect/certs';
  private readonly issuer = process.env.AUTH_JWT_ISSUER_URI
    ?? 'http://localhost:8180/realms/skytrace';
  private readonly audience = process.env.AUTH_JWT_AUDIENCE ?? 'skytrace-web';
  private readonly signingKeys = new Map<string, CachedSigningKey>();
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
    if (header.alg !== 'RS256' || typeof header.kid !== 'string') {
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
    const cached = this.signingKeys.get(kid);
    if (cached && cached.expiresAt > Date.now()) {
      return cached.key;
    }

    await this.refreshSigningKeys();
    const refreshed = this.signingKeys.get(kid);
    if (!refreshed) {
      throw this.unauthorized('找不到访问令牌对应的签名公钥');
    }
    return refreshed.key;
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
      const keySet = await response.json() as JsonWebKeySet;
      const nextExpiry = Date.now() + JWKS_CACHE_MILLISECONDS;
      let imported = 0;

      for (const jwk of keySet.keys ?? []) {
        if (!jwk.kid || jwk.kty !== 'RSA') {
          continue;
        }
        if (jwk.alg && jwk.alg !== 'RS256') {
          continue;
        }
        if (jwk.use && jwk.use !== 'sig') {
          continue;
        }
        this.signingKeys.set(jwk.kid, {
          key: createPublicKey({ key: jwk, format: 'jwk' }),
          expiresAt: nextExpiry,
        });
        imported += 1;
      }

      if (imported === 0) {
        throw new Error('JWKS 中没有可用的 RSA 签名公钥');
      }
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
