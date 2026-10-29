FROM gradle:9.8.2-jdk21 AS build
WORKDIR /workspace

# Resolve dependencies in their own layer so source changes don't re-download them.
COPY settings.gradle build.gradle ./
RUN gradle dependencies --no-daemon -q > /dev/null

COPY src src
RUN gradle bootJar --no-daemon -q \
 && java -Djarmode=tools -jar build/libs/app.jar extract --layers --launcher --destination build/extracted

# --- runtime: JRE only, layered jar (dependencies change rarely, app code often), non-root user
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --uid 10001 app
USER app

COPY --from=build /workspace/build/extracted/dependencies/ ./
COPY --from=build /workspace/build/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/build/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/build/extracted/application/ ./

EXPOSE 8081

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=5 \
  CMD curl -fsS http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
