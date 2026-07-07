# Stage 1: Build the Vue application
ARG IMAGE_REGISTRY_PREFIX=
FROM ${IMAGE_REGISTRY_PREFIX}node:20-alpine AS builder

# Accept build arguments
ARG VITE_API_BASE_URL
ARG VITE_TUTOR_API_BASE_URL
ARG VITE_ADMIN_LOGIN_SECRET
ARG VITE_ADMIN_PORTAL_PATH
# 内网构建镜像源（留空=使用官方源；内网填入 Nexus npm 代理地址）
# 示例: http://172.20.14.249:8081/repository/npm-proxy/
ARG NPM_REGISTRY=
# Alpine 内网镜像源（留空=使用官方源；内网填入 Nexus Alpine 代理地址）
# 示例: http://172.20.14.249:8081/repository/alpine-mirror/
ARG ALPINE_MIRROR=

# Set environment variables for the build process
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ENV VITE_TUTOR_API_BASE_URL=$VITE_TUTOR_API_BASE_URL
ENV VITE_ADMIN_LOGIN_SECRET=$VITE_ADMIN_LOGIN_SECRET
ENV VITE_ADMIN_PORTAL_PATH=$VITE_ADMIN_PORTAL_PATH

WORKDIR /app

# Copy package.json and package-lock.json
COPY package*.json ./

# 使用国内 Alpine 镜像源（留空则使用阿里云公网源）
RUN if [ -n "$ALPINE_MIRROR" ]; then \
      ALPINE_VER=$(cat /etc/alpine-release | cut -d. -f1-2); \
      printf '%s\n' "${ALPINE_MIRROR%/}/v${ALPINE_VER}/main" "${ALPINE_MIRROR%/}/v${ALPINE_VER}/community" > /etc/apk/repositories; \
    else \
      echo "https://mirrors.aliyun.com/alpine/v3.23/main" > /etc/apk/repositories; \
      echo "https://mirrors.aliyun.com/alpine/v3.23/community" >> /etc/apk/repositories; \
    fi

# Install all dependencies (including devDependencies for vite build)
# 内网构建时通过 NPM_REGISTRY 参数指向内网 Nexus npm 代理仓库
RUN if [ -n "$NPM_REGISTRY" ]; then npm config set registry "$NPM_REGISTRY"; fi && npm ci --include=dev

# Copy source code
COPY . .

# Build the application
RUN npm run build

# Stage 2: Serve the application with Nginx
ARG IMAGE_REGISTRY_PREFIX=
FROM ${IMAGE_REGISTRY_PREFIX}nginx:stable-alpine

# Copy built assets from builder stage
COPY --from=builder /app/dist /usr/share/nginx/html

# Copy maintenance page (for auto-update maintenance mode)
COPY maintenance.html /usr/share/nginx/html/maintenance.html

# Copy custom Nginx configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Expose port 80
EXPOSE 80

# Start Nginx
CMD ["nginx", "-g", "daemon off;"]
