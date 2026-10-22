# syntax=docker/dockerfile:1
# Production image (port of the Ruby Dockerfile:11-67). Build: docker build --build-arg APP_VERSION=v1.2.3 -t open-banking-kotlin .

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon --version
COPY config ./config
COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test -x detekt

FROM eclipse-temurin:21-jre AS base
# pg_dump 17 (pre-migration backups) comes from the PostgreSQL apt repository; curl for the healthcheck.
RUN apt-get update -qq && \
    apt-get install --no-install-recommends -y curl ca-certificates gnupg && \
    install -d /usr/share/postgresql-common/pgdg && \
    curl -fsSL -o /usr/share/postgresql-common/pgdg/apt.postgresql.org.asc https://www.postgresql.org/media/keys/ACCC4CF8.asc && \
    . /etc/os-release && \
    echo "deb [signed-by=/usr/share/postgresql-common/pgdg/apt.postgresql.org.asc] https://apt.postgresql.org/pub/repos/apt ${VERSION_CODENAME}-pgdg main" \
      > /etc/apt/sources.list.d/pgdg.list && \
    apt-get update -qq && \
    apt-get install --no-install-recommends -y postgresql-client-17 && \
    apt-get purge -y gnupg && apt-get autoremove -y && \
    rm -rf /var/lib/apt/lists /var/cache/apt/archives

ENV RAILS_ENV="production"
# Same paths and uid/gid 1000 as the Ruby image, so the existing app_secrets volume and ./backups keep working.
WORKDIR /rails

RUN (userdel --remove ubuntu 2>/dev/null || true) && \
    groupadd --system --gid 1000 rails && \
    useradd rails --uid 1000 --gid 1000 --create-home --shell /bin/bash && \
    mkdir -p /rails/secrets /rails/backups /rails/bin && chown -R rails:rails /rails

COPY --chown=rails:rails --from=build /src/build/libs/app.jar /rails/app.jar
COPY --chown=rails:rails bin/docker-entrypoint bin/docker-migrate /rails/bin/

# Release tag for the footer (AppProperties.version); local builds get "dev". Kept last to preserve the layer cache.
ARG APP_VERSION=dev
RUN echo "$APP_VERSION" > /rails/VERSION && chown rails:rails /rails/VERSION

USER 1000:1000
ENTRYPOINT ["/rails/bin/docker-entrypoint"]
EXPOSE 3000
CMD ["java", "-XX:MaxRAMPercentage=75", "-jar", "/rails/app.jar"]
