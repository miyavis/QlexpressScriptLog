# Build the Vue frontend with a Node version supported by Vite 6 / Vitest 4.
FROM node:20-alpine AS frontend
WORKDIR /workspace/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# Build the Spring Boot fat jar with the project's Gradle wrapper on Java 17.
FROM gradle:7.6.4-jdk17 AS backend
WORKDIR /workspace
COPY . .
COPY --from=frontend /workspace/frontend/dist ./frontend/dist
# 多模块后 fat jar 产自 rulelog-platform 子模块（不是根目录 build/libs）
RUN chmod +x ./gradlew \
    && ./gradlew :rulelog-platform:bootJar --no-daemon \
    && find rulelog-platform/build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' -exec cp {} /workspace/app.jar \;

# Keep the runtime image small; Java 17 runs this Java 17-targeted application.
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=backend /workspace/app.jar ./app.jar
EXPOSE 8080
CMD ["sh", "-c", "exec java -Dfile.encoding=UTF-8 -Dserver.port=${PORT:-8080} -jar /app/app.jar"]
