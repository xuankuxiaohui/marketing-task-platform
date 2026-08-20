# Layered runtime image (12-Factor V / 14-deployment §4). Build context = module target/.
# ARG JAR_FILE is the Boot fat jar (admin-app-*.jar or portal-app-*-exec.jar).
ARG JDK_IMAGE=eclipse-temurin:26-jre-noble

FROM ${JDK_IMAGE} AS extract
WORKDIR /src
ARG JAR_FILE
COPY ${JAR_FILE} app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination /src/extracted --launcher

FROM ${JDK_IMAGE}
ENV TZ=UTC \
    LANG=C.UTF-8
RUN groupadd --system --gid 10001 mkt \
    && useradd --system --uid 10001 --gid mkt --no-create-home --shell /usr/sbin/nologin mkt \
    && apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=extract --chown=mkt:mkt /src/extracted/dependencies/ ./
COPY --from=extract --chown=mkt:mkt /src/extracted/spring-boot-loader/ ./
COPY --from=extract --chown=mkt:mkt /src/extracted/snapshot-dependencies/ ./
COPY --from=extract --chown=mkt:mkt /src/extracted/application/ ./
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
