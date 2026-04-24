# ─────────────────────────────────────────────
# 阶段一：构建（Maven + JDK 17）
# ─────────────────────────────────────────────
ARG IMAGE_REGISTRY_PREFIX=
FROM ${IMAGE_REGISTRY_PREFIX}maven:3.8-openjdk-17 AS builder

WORKDIR /app

# 先复制 pom.xml 并下载依赖，利用 Docker 层缓存
# 只有 pom.xml 变更时才重新下载依赖，加速后续构建
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 复制源码并打包（跳过单元测试加快 CI 构建速度）
COPY src ./src
RUN mvn package -DskipTests -B

# ─────────────────────────────────────────────
# 阶段二：运行（轻量 JRE 17）
# ─────────────────────────────────────────────
ARG IMAGE_REGISTRY_PREFIX=
FROM ${IMAGE_REGISTRY_PREFIX}eclipse-temurin:17-jre-alpine

WORKDIR /app

# 安装 curl（healthcheck）、tzdata（时区）、mysql-client + mariadb-connector-c（mysqldump 备份/恢复）
# mariadb-connector-c 提供 caching_sha2_password 插件，使 MariaDB 客户端兼容 MySQL 8.0
RUN apk add --no-cache curl tzdata mysql-client mariadb-connector-c \
    && ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone

# 从构建阶段复制可执行 jar
COPY --from=builder /app/target/*.jar app.jar

# 自定义 Java 安全属性：从禁用列表中移除 rsa_pkcs1_sha1
# CAS 服务器(ids.imau.edu.cn)的 TLS 握手使用此算法，Java 17+ 默认禁用导致 SSLHandshakeException
# 仅覆盖 jdk.tls.disabledAlgorithms，其余安全策略保持 JRE 默认值
COPY java-security.properties /app/java-security.properties

# 创建日志、上传和备份目录
RUN mkdir -p /app/logs /app/uploads /app/backups

# JVM 内存调优（适用于 4 核 8GB 服务器）
# -Xms1g          : 初始堆大小 1GB，减少启动时扩容开销
# -Xmx4g          : 最大堆 4GB（留 4GB 给 OS/非堆）
# -XX:+UseG1GC    : G1 垃圾收集器，低延迟优先
# -XX:MaxGCPauseMillis=200  : 目标最大 GC 停顿 200ms
# -XX:+HeapDumpOnOutOfMemoryError : OOM 时自动 dump，便于排查
# -Djava.security.egd=file:/dev/./urandom : 加快 SecureRandom 初始化
# -Djava.security.properties=/app/java-security.properties :
#   自定义安全属性，从 jdk.tls.disabledAlgorithms 移除 rsa_pkcs1_sha1
#   因 CAS 服务器(ids.imau.edu.cn)的 TLS 握手使用此已弃用算法
ENV JAVA_OPTS="-Xms1g -Xmx4g \
               -XX:+UseG1GC \
               -XX:MaxGCPauseMillis=200 \
               -XX:+HeapDumpOnOutOfMemoryError \
               -XX:HeapDumpPath=/tmp/heap.hprof \
               -Djava.security.egd=file:/dev/./urandom \
               -Djava.security.properties=/app/java-security.properties"

EXPOSE 8080

# 使用 sh -c 以便 $JAVA_OPTS 正确展开
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
