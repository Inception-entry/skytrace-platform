import { HttpService } from '@nestjs/axios';
import {
  HttpException,
  Inject,
  Injectable,
  Scope,
} from '@nestjs/common';
import { REQUEST } from '@nestjs/core';
import { isAxiosError } from 'axios';
import { Readable } from 'node:stream';
import { firstValueFrom } from 'rxjs';
import type { AuthenticatedHttpRequest } from '../../auth/http-auth.types';

@Injectable({ scope: Scope.REQUEST })
export class JavaClientService {
  private readonly baseUrl = process.env.JAVA_BASE_URL ?? 'http://localhost:8081';

  constructor(
    private readonly httpService: HttpService,
    @Inject(REQUEST)
    private readonly request: AuthenticatedHttpRequest,
  ) {}

  async get<T>(path: string): Promise<T> {
    try {
      const response = await firstValueFrom(
        this.httpService.get<T>(
          `${this.baseUrl}/api${path}`,
          { headers: this.downstreamHeaders() },
        ),
      );
      return response.data;
    } catch (error) {
      this.rethrowUpstreamError(error);
    }
  }

  async post<T>(
    path: string,
    body: unknown,
    timeout = 5000,
  ): Promise<T> {
    try {
      const response = await firstValueFrom(
        this.httpService.post<T>(
          `${this.baseUrl}/api${path}`,
          body,
          {
            timeout,
            headers: this.downstreamHeaders(),
          },
        ),
      );
      return response.data;
    } catch (error) {
      this.rethrowUpstreamError(error);
    }
  }

  async postStream(
    path: string,
    body: unknown,
    timeout = 300_000,
  ): Promise<Readable> {
    try {
      const response = await firstValueFrom(
        this.httpService.post<Readable>(
          `${this.baseUrl}/api${path}`,
          body,
          {
            timeout,
            responseType: 'stream',
            headers: {
              ...this.downstreamHeaders(),
              Accept: 'text/event-stream',
              'Content-Type': 'application/json',
            },
          },
        ),
      );
      return response.data;
    } catch (error) {
      if (isAxiosError(error) && error.response) {
        throw new HttpException(
          'Java 流式分析服务请求失败',
          error.response.status,
        );
      }
      throw error;
    }
  }

  async put<T>(
    path: string,
    body: unknown,
    timeout = 5000,
  ): Promise<T> {
    try {
      const response = await firstValueFrom(
        this.httpService.put<T>(
          `${this.baseUrl}/api${path}`,
          body,
          {
            timeout,
            headers: this.downstreamHeaders(),
          },
        ),
      );
      return response.data;
    } catch (error) {
      this.rethrowUpstreamError(error);
    }
  }

  async postMultipart<T>(
    path: string,
    file: {
      buffer: Buffer;
      originalname: string;
      mimetype: string;
    },
    fields: Record<string, string | undefined> = {},
    timeout = 180_000,
  ): Promise<T> {
    const formData = new FormData();
    formData.append(
      'file',
      new Blob([new Uint8Array(file.buffer)], {
        type: file.mimetype || 'application/octet-stream',
      }),
      file.originalname,
    );
    for (const [key, value] of Object.entries(fields)) {
      if (value) {
        formData.append(key, value);
      }
    }

    try {
      const response = await firstValueFrom(
        this.httpService.post<T>(
          `${this.baseUrl}/api${path}`,
          formData,
          {
            timeout,
            headers: this.downstreamHeaders(),
          },
        ),
      );
      return response.data;
    } catch (error) {
      this.rethrowUpstreamError(error);
    }
  }

  async delete<T>(path: string, timeout = 5000): Promise<T> {
    try {
      const response = await firstValueFrom(
        this.httpService.delete<T>(`${this.baseUrl}/api${path}`, {
          timeout,
          headers: this.downstreamHeaders(),
        }),
      );
      return response.data;
    } catch (error) {
      this.rethrowUpstreamError(error);
    }
  }

  private rethrowUpstreamError(error: unknown): never {
    if (isAxiosError(error) && error.response) {
      throw new HttpException(
        error.response.data ?? 'Java 服务请求失败',
        error.response.status,
      );
    }
    throw error;
  }

  private downstreamHeaders(): Record<string, string> {
    const headers: Record<string, string> = {};
    if (this.request.accessToken) {
      headers.Authorization = `Bearer ${this.request.accessToken}`;
    }

    const requestId = this.headerValue(
      this.request.headers['x-request-id'],
    );
    if (requestId) {
      headers['X-Request-Id'] = requestId;
    }

    const forwardedFor = this.headerValue(
      this.request.headers['x-forwarded-for'],
    );
    const clientIp = forwardedFor?.split(',', 1)[0].trim()
      || this.request.ip
      || this.request.socket?.remoteAddress;
    if (clientIp) {
      headers['X-Forwarded-For'] = clientIp;
    }
    return headers;
  }

  private headerValue(
    value: string | string[] | undefined,
  ): string | undefined {
    return Array.isArray(value) ? value[0] : value;
  }
}
