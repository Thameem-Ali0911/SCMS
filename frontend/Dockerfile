# frontend/Dockerfile — multi-stage build
#
# MENTOR NOTE — fixing the v1.3 finding: "the Vite proxy is development-only
# — in production the React build produces static files that need a web
# server, and the backend needs a separate domain/port... the production
# frontend serving strategy is completely undocumented."
#
# Stage 1 (build): Node image builds the static production bundle (`vite build`).
# Stage 2 (runtime): Nginx serves those static files AND reverse-proxies
# `/api/*` to the backend container (see nginx.conf) — so the browser only
# ever talks to ONE origin in production, exactly like the Vite dev proxy
# did in development. No CORS configuration is even needed between the
# frontend and its own backend when deployed this way (CORS_ORIGINS is still
# needed if OTHER frontends/sites integrate with this backend directly).
FROM node:20-alpine AS build
WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM nginx:1.27-alpine AS runtime
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=5 \
  CMD wget -qO- http://localhost/ > /dev/null 2>&1 || exit 1

CMD ["nginx", "-g", "daemon off;"]
