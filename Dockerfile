# 多阶段构建 Dockerfile
# 构建 CoExistree 应用

# =================== 阶段 1: 构建后端 ===================
FROM eclipse-temurin:21-jdk-alpine AS backend-build

WORKDIR /build

# 复制 Maven Wrapper 和配置
COPY backend/.mvn ./.mvn
COPY backend/mvnw backend/pom.xml ./

# 下载依赖（利用缓存层）
RUN ./mvnw dependency:go-offline -B

# 复制源码并构建
COPY backend/src ./src
RUN ./mvnw clean package -DskipTests -B && \
    mkdir -p target/dependency && \
    (cd target/dependency; jar -xf ../*.jar)

# =================== 阶段 2: 构建前端 ===================
FROM node:20-alpine AS frontend-build

WORKDIR /build

# 复制依赖配置
COPY frontend/package*.json ./
RUN npm ci

# 复制源码并构建
COPY frontend/ ./
RUN npm run build

# =================== 阶段 3: 后端运行时镜像 ===================
FROM eclipse-temurin:21-jre-alpine AS backend-runtime

# 安装必要的工具
RUN apk add --no-cache curl tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone && \
    apk del tzdata

# 创建应用用户
RUN addgroup -S coexistree && adduser -S coexistree -G coexistree

# 工作目录
WORKDIR /app

# 从后端构建复制依赖和类
COPY --from=backend-build /build/target/dependency/BOOT-INF/lib ./lib
COPY --from=backend-build /build/target/dependency/META-INF ./META-INF
COPY --from=backend-build /build/target/dependency/BOOT-INF/classes ./classes

# 创建数据目录并设置权限
RUN mkdir -p /app/data && chown -R coexistree:coexistree /app

# 切换到非 root 用户
USER coexistree:coexistree

# 环境变量
ENV JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC" \
    SERVER_PORT=8080 \
    SPRING_PROFILES_ACTIVE=prod \
    STORAGE_PATH=/app/data

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:${SERVER_PORT}/actuator/health || exit 1

# 暴露端口
EXPOSE 8080

# 启动命令
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -cp classes:lib/* io.github.xiaoailazy.coexistree.CoExistreeApplication"]

# =================== 阶段 4: Nginx 运行时镜像 ===================
FROM nginx:alpine AS nginx-runtime

# 从前端构建复制静态文件
COPY --from=frontend-build /build/dist /app/static

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD curl -f http://localhost/ || exit 1

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
