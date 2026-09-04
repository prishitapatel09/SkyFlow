FROM node:22-alpine AS build
WORKDIR /build

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# Baked in at build time, so a different API origin needs a different image.
ARG VITE_API_BASE_URL=""
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
# Only GATEWAY_HOST is substituted into the template - nginx's own $host/$uri stay intact.
ENV GATEWAY_HOST=api-gateway:8080
ENV NGINX_ENVSUBST_FILTER=GATEWAY_HOST
COPY --from=build /build/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD wget -qO- http://localhost/healthz > /dev/null || exit 1
