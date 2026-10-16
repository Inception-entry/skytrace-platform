import { Injectable } from '@nestjs/common';
import type { ClientLogDto } from './client-log.dto';

function redact(value: string): string {
  return value.replace(/token|password|authorization|secret|cookie|credential/gi, '[redacted]');
}
const WINDOW_MS = 60_000;
const MAX_PER_WINDOW = 30;

@Injectable()
export class ClientLogService {
  private readonly hits = new Map<string, number[]>();

  accept(entry: ClientLogDto, ip: string | undefined, now = Date.now()): boolean {
    if (!this.allow(ip ?? '', now)) {
      return false;
    }
    const line = formatClientLog(entry);
    if (entry.level === 'error') {
      console.error(line);
    } else {
      console.warn(line);
    }
    return true;
  }

  private allow(ip: string, now: number): boolean {
    const key = ip.trim() || 'unknown';
    if (this.hits.size > 2000) {
      this.hits.clear();
    }
    const cutoff = now - WINDOW_MS;
    const recent = (this.hits.get(key) ?? []).filter((time) => time > cutoff);
    if (recent.length >= MAX_PER_WINDOW) {
      this.hits.set(key, recent);
      return false;
    }
    recent.push(now);
    this.hits.set(key, recent);
    return true;
  }
}

export function formatClientLog(entry: ClientLogDto): string {
  const message = redact(entry.message).slice(0, 300);
  const path = redact(entry.path ?? '').slice(0, 200);
  const detail = formatDetail(entry.detail);
  const head = `[skytrace-web] ${new Date().toISOString()} ${entry.level.toUpperCase()} ${entry.scope} ${message} path=${path}`;
  return detail ? `${head} ${detail}` : head;
}

function formatDetail(detail: ClientLogDto['detail']): string {
  if (!detail) return '';
  const parts: string[] = [];
  if (detail.status != null) parts.push(`status=${detail.status}`);
  if (detail.method) parts.push(`method=${detail.method.toUpperCase()}`);
  if (detail.url) parts.push(`url=${stripQuery(detail.url)}`);
  if (detail.code) parts.push(`code=${redact(detail.code)}`);
  if (detail.serverMessage) parts.push(`serverMessage=${redact(detail.serverMessage)}`);
  return parts.join(' ');
}

function stripQuery(url: string): string {
  const query = url.indexOf('?');
  return redact(query === -1 ? url : url.slice(0, query));
}

