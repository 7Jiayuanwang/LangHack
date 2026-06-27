ARG IMAGE_REGISTRY_PREFIX=
FROM ${IMAGE_REGISTRY_PREFIX}mysql:8.0

# Build context: docker/
# 从库仅复制表结构脚本（00、04、08），数据通过 init-replication.sh 的 mysqldump 从主库同步
# 不再需要 03-import-backup.sh / 05-import-incremental-backup.sh / 06-import-ecdict.sh，
# 因为它们检测 MYSQL_ROLE=slave 后直接跳过，且数据由 mysqldump 提供

# 表结构脚本
COPY mysql/init/00-base-tables.sql /docker-entrypoint-initdb.d/00-base-tables.sql
COPY init-db.sh /docker-entrypoint-initdb.d/01-init-db.sh
COPY mysql/init/01-grant-replication-client-to-root.sh /docker-entrypoint-initdb.d/01-grant-replication-client-to-root.sh
COPY mysql/init/04-apply-all-migrations.sql /opt/langhack/04-apply-all-migrations.sql
COPY mysql/init/04-apply-all-migrations.sh /docker-entrypoint-initdb.d/04-apply-all-migrations.sh
COPY mysql/init/08-v16-school-tables.sql /docker-entrypoint-initdb.d/08-v16-school-tables.sql

# 复制初始化标记 + 实际初始化脚本
# 注意：init-replication.sh 不放在 docker-entrypoint-initdb.d 中，
# 避免首次建卷时自动执行耗时的主库 dump / 导入 / 复制配置流程。
# 由 06-create-replication-marker.sh 将其复制到 /var/lib/mysql/，
# 之后由 entrypoint-wrapper.sh（每次启动时主动检查）或 deploy.sh / auto-update.sh
# （后置兜底）按需调用。
COPY mysql/slave/06-create-replication-marker.sh /docker-entrypoint-initdb.d/07-create-replication-marker.sh
COPY mysql/slave/init-replication.sh /opt/langhack/init-replication.sh
COPY mysql/slave/my.cnf /etc/mysql/conf.d/slave.cnf
COPY mysql/slave/entrypoint-wrapper.sh /usr/local/bin/slave-wrapper.sh

RUN chmod +x /docker-entrypoint-initdb.d/*.sh /opt/langhack/init-replication.sh /usr/local/bin/slave-wrapper.sh

# 使用自定义 entrypoint wrapper 确保每次容器启动时都检查/修复复制
# 与 deploy.sh / auto-update.sh 后置检查互为兜底
ENTRYPOINT ["/usr/local/bin/slave-wrapper.sh"]
