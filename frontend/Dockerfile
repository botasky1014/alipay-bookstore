# Build context is the repository root (see docker-compose.yml).
# certs/ holds optional extra CA certificates, needed only on networks
# with TLS inspection.

FROM node:24-alpine AS build
COPY certs/ /tmp/certs/
RUN cat /tmp/certs/*.crt > /tmp/extra-ca.pem 2>/dev/null || true
ENV NODE_EXTRA_CA_CERTS=/tmp/extra-ca.pem
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM nginx:stable-alpine
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
