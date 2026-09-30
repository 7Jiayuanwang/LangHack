# ─────────────────────────────────────────────
# 构建阶段（容器内用 Maven 编译，依赖走内网 Nexus）
# ─────────────────────────────────────────────
ARG IMAGE_REGISTRY_PREFIX=
FROM ${IMAGE_REGISTRY_PREFIX}maven:3.8-openjdk-17 AS builder

# Maven 内网镜像源（留空=使用 pom 默认；内网填入 Nexus maven-public 地址）
ARG MAVEN_MIRROR_URL=

WORKDIR /build

# 若配置了内网 Nexus，生成指向它的 settings.xml
RUN if [ -n "$MAVEN_MIRROR_URL" ]; then \
        mkdir -p /root/.m2; \
        printf '%s\n' \
          '<settings>' \
          '  <mirrors><mirror>' \
          '    <id>nexus</id><mirrorOf>*</mirrorOf>' \
          "    <url>${MAVEN_MIRROR_URL}</url>" \
          '  </mirror></mirrors>' \
          '</settings>' > /root/.m2/settings.xml; \
    fi

# 先拷 pom 预热依赖缓存（利用 Docker 层缓存，源码变动不必重下依赖）
COPY pom.xml ./
RUN mvn -B dependency:go-offline -DskipTests || true

# 拷源码并打包
COPY src ./src
RUN mvn -B clean package -DskipTests

# ─────────────────────────────────────────────
# 运行阶段
# ─────────────────────────────────────────────
FROM ${IMAGE_REGISTRY_PREFIX}eclipse-temurin:17-jre-alpine

# Alpine 内网镜像源（留空=使用官方源；内网填入 Nexus Alpine 代理地址）
ARG ALPINE_MIRROR=

WORKDIR /app

# 安装 curl（healthcheck）、tzdata（时区）、离线文章朗读引擎，以及数据库备份工具
RUN ALPINE_VER=$(cat /etc/alpine-release | cut -d. -f1-2) \
    && if [ -n "$ALPINE_MIRROR" ]; then \
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
        espeak-ng \
        docker-cli \
        mysql-client \
        mariadb-connector-c \
    && ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone

# 从构建阶段复制编译好的可执行 jar
COPY --from=builder /build/target/english-learning-platform-1.0.0.jar app.jar

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
