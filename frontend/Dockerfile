FROM node:22-alpine AS build

WORKDIR /app

COPY package*.json ./
# registry 偶发断连时重试。
RUN set -eu; \
    npm_retry() { \
      i=0; \
      until "$@"; do \
        i=$((i + 1)); \
        [ "$i" -ge 5 ] && exit 1; \
        echo "npm registry flake (attempt $i/5), retrying..." >&2; \
        sleep $((i * 8)); \
      done; \
    }; \
    npm_retry npm ci

COPY . .
RUN npm run build

FROM nginx:1.29-alpine

RUN apk upgrade --no-cache \
    && sed -i '/user  nginx;/d' /etc/nginx/nginx.conf \
    && sed -i 's,/var/run/nginx.pid,/tmp/nginx.pid,' /etc/nginx/nginx.conf \
    && sed -i 's,/run/nginx.pid,/tmp/nginx.pid,' /etc/nginx/nginx.conf \
    && sed -i '/^http {/a \    proxy_temp_path /tmp/proxy_temp;\n    client_body_temp_path /tmp/client_temp;\n    fastcgi_temp_path /tmp/fastcgi_temp;\n    uwsgi_temp_path /tmp/uwsgi_temp;\n    scgi_temp_path /tmp/scgi_temp;' /etc/nginx/nginx.conf \
    && rm -f /etc/nginx/conf.d/default.conf \
    && chown -R nginx:nginx /usr/share/nginx/html /var/cache/nginx /etc/nginx/conf.d \
    && touch /tmp/nginx.pid \
    && chown nginx:nginx /tmp/nginx.pid
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build --chown=nginx:nginx /app/dist /usr/share/nginx/html
RUN chown nginx:nginx /etc/nginx/templates/default.conf.template
USER nginx

EXPOSE 8080

CMD ["nginx", "-g", "daemon off;"]
