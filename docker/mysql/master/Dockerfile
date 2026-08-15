ARG IMAGE_REGISTRY_PREFIX=
FROM ${IMAGE_REGISTRY_PREFIX}mysql:8.0

# Build context: docker/
# 复制 init 脚本到镜像内，避免 Windows Docker Desktop bind-mount 缓存问题
COPY mysql/init/00-base-tables.sql /docker-entrypoint-initdb.d/00-base-tables.sql
COPY init-db.sh /docker-entrypoint-initdb.d/01-init-db.sh
COPY mysql/init/01-grant-replication-client-to-root.sh /docker-entrypoint-initdb.d/01-grant-replication-client-to-root.sh
COPY mysql/init/01-create-replication-user.sh /docker-entrypoint-initdb.d/02-create-replication-user.sh
COPY mysql/init/03-import-backup.sh /docker-entrypoint-initdb.d/03-import-backup.sh
COPY mysql/init/04-apply-all-migrations.sql /opt/langhack/04-apply-all-migrations.sql
COPY mysql/init/04-apply-all-migrations.sh /docker-entrypoint-initdb.d/04-apply-all-migrations.sh
COPY mysql/init/05-import-incremental-backup.sh /docker-entrypoint-initdb.d/05-import-incremental-backup.sh
COPY mysql/init/06-import-ecdict.sh /docker-entrypoint-initdb.d/06-import-ecdict.sh
COPY mysql/init/08-v16-school-tables.sql /docker-entrypoint-initdb.d/08-v16-school-tables.sql
COPY mysql/master/my.cnf /etc/mysql/conf.d/master.cnf

# 复制 seed 目录（生产全量备份），供 03-import-backup.sh 自动加载
#COPY mysql/init/seed/ /docker-entrypoint-initdb.d/seed/

RUN chmod +x /docker-entrypoint-initdb.d/*.sh
