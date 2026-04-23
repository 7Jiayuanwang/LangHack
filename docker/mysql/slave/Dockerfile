ARG IMAGE_REGISTRY_PREFIX=
FROM ${IMAGE_REGISTRY_PREFIX}mysql:8.0

# Build context: docker/
# 从库仅复制表结构脚本（00、04、08），数据通过 init-replication.sh 的 mysqldump 从主库同步
# 不再需要 03-import-backup.sh / 05-import-incremental-backup.sh / 06-import-ecdict.sh，
# 因为它们检测 MYSQL_ROLE=slave 后直接跳过，且数据由 mysqldump 提供

# 表结构脚本
COPY mysql/init/00-base-tables.sql /docker-entrypoint-initdb.d/00-base-tables.sql
COPY init-db.sh /docker-entrypoint-initdb.d/01-init-db.sh
COPY mysql/init/04-apply-all-migrations.sql /docker-entrypoint-initdb.d/04-apply-all-migrations.sql
COPY mysql/init/08-v16-school-tables.sql /docker-entrypoint-initdb.d/08-v16-school-tables.sql

# 复制初始化标记 + 实际初始化脚本
COPY mysql/slave/06-create-replication-marker.sh /docker-entrypoint-initdb.d/07-create-replication-marker.sh
COPY mysql/slave/init-replication.sh /docker-entrypoint-initdb.d/init-replication.sh
COPY mysql/slave/my.cnf /etc/mysql/conf.d/slave.cnf

RUN chmod +x /docker-entrypoint-initdb.d/*.sh
