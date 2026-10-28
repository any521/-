# 点废成金 v2.0 后端镜像（A5）：多阶段构建，产物为 dianfei-admin 可执行 jar
# 构建：docker build -t dianfei/back:2.0.0 back/
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
# 先拷 pom 利用依赖缓存层
COPY pom.xml .
COPY dianfei-common/pom.xml dianfei-common/
COPY dianfei-system/pom.xml dianfei-system/
COPY dianfei-business/pom.xml dianfei-business/
COPY dianfei-ai/pom.xml dianfei-ai/
COPY dianfei-dispatch/pom.xml dianfei-dispatch/
COPY dianfei-admin/pom.xml dianfei-admin/
# 阿里云镜像 + 显式网络超时（云服务器直连中央仓库会长时间挂起）
COPY settings.xml .
RUN mvn -q -s settings.xml dependency:go-offline -pl dianfei-admin -am -Dmaven.wagon.http.connectionTimeout=30000 -Dmaven.wagon.http.readTimeout=120000 || true
COPY . .
RUN mvn -q -s settings.xml clean package -DskipTests -Dmaven.wagon.http.connectionTimeout=30000 -Dmaven.wagon.http.readTimeout=120000

FROM eclipse-temurin:17-jre
WORKDIR /app
# finalName=dianfei-admin（无版本号），COPY 通配符匹配不到，必须精确文件名
COPY --from=builder /build/dianfei-admin/target/dianfei-admin.jar app.jar
# 本地磁盘上传目录 + 日志目录（compose 挂载 back-upload 卷持久化；日志供 GC/OOM dump 落盘）
RUN mkdir -p /app/upload /app/logs \
    # S1：healthcheck 探活依赖 curl；安装后清理索引减小镜像体积
    && apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    # S1：以非 root 运行（容器逃逸面收窄），upload/logs 目录归属应用用户
    && groupadd -r appuser && useradd -r -g appuser -d /app appuser \
    && chown -R appuser:appuser /app
USER appuser
# W-06: JVM 参数优化（堆上限提升至 2g + RAM 占比控制 + OOM 自动 dump + GC 日志轮转）
ENV JAVA_OPTS="-Xms512m -Xmx2g -XX:MaxRAMPercentage=75 -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/logs/ -Xlog:gc*:file=/app/logs/gc.log:time,uptime:filecount=5,filesize=10M" \
    SPRING_PROFILES_ACTIVE=prod \
    TZ=Asia/Shanghai
EXPOSE 8080
# S1：健康探活（compose healthcheck 与 k8s readiness 同源）；仅状态码校验，不输出敏感信息
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://127.0.0.1:8080/api/health > /dev/null || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
