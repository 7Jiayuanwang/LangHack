# ─────────────────────────────────────────────
# 运行阶段（直接使用本地已打好的jar，不在容器内编译）
# jar包由云电脑预先打好：english-learning-platform-1.0.0.jar
# ─────────────────────────────────────────────
ARG IMAGE_REGISTRY_PREFIX=
FROM ${IMAGE_REGISTRY_PREFIX}eclipse-temurin:17-jre-alpine

# Alpine 内网镜像源（留空=使用官方源；内网填入 Nexus Alpine 代理地址）
ARG ALPINE_MIRROR=

WORKDIR /app

# 安装 curl（healthcheck）、tzdata（时区）、mysql-client + mariadb-connector-c（mysqldump 备份/恢复）
RUN if [ -n "$ALPINE_MIRROR" ]; then \
        ALPINE_VER=$(cat /etc/alpine-release | cut -d. -f1-2); \
        printf '%s\n' "${ALPINE_MIRROR%/}/v${ALPINE_VER}/main" "${ALPINE_MIRROR%/}/v${ALPINE_VER}/community" > /etc/apk/repositories; \
    else \
        printf '%s\n' \
            "https://mirrors.aliyun.com/alpine/v${ALPINE_VER}/main" \
            "https://mirrors.aliyun.com/alpine/v${ALPINE_VER}/community" \
            "https://dl-cdn.alpinelinux.org/alpine/v${ALPINE_VER}/main" \
            "https://dl-cdn.alpinelinux.org/alpine/v${ALPINE_VER}/community" \
            > /etc/apk/repositories; \
    fi \
    && apk update || true \
    && apk add --no-cache \
        curl \
        tzdata \
        mysql-client \
        mariadb-connector-c \
    && ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone

# 直接复制本地已打好的可执行 jar（不在容器内跑maven编译）
COPY english-learning-platform-1.0.0.jar app.jar

# 自定义 Java 安全属性：从禁用列表中移除 rsa_pkcs1_sha1
COPY java-security.properties /app/java-security.properties

# 创建日志、上传和备份目录
RUN mkdir -p /app/logs /app/uploads /app/backups

# JVM 内存调优
ENV JAVA_OPTS="-Xms1g -Xmx4g \
               -XX:+UseG1GC \
               -XX:MaxGCPauseMillis=200 \
               -XX:+HeapDumpOnOutOfMemoryError \
               -XX:HeapDumpPath=/tmp/heap.hprof \
               -Djava.security.egd=file:/dev/./urandom \
               -Djava.security.properties=/app/java-security.properties"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
