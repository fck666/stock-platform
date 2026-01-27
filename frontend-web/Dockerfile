FROM node:20-alpine AS build

WORKDIR /workspace
COPY frontend-web/package.json frontend-web/package-lock.json frontend-web/
RUN cd frontend-web && npm ci
COPY frontend-web frontend-web
RUN cd frontend-web && npm run build

FROM nginx:1.27-alpine
COPY docker/app/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/frontend-web/dist /usr/share/nginx/html
EXPOSE 80

