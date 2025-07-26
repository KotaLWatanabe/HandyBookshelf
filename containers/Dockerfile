# HandyBookshelf Application Dockerfile
# Stage 1: Build Stage
FROM ghcr.io/graalvm/graalvm-community:24 AS build

WORKDIR /app

# Install SBT
RUN apt-get update && \
    apt-get install -y curl && \
    echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | tee /etc/apt/sources.list.d/sbt.list && \
    echo "deb https://repo.scala-sbt.org/scalasbt/debian /" | tee /etc/apt/sources.list.d/sbt_old.list && \
    curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | gpg --no-default-keyring --keyring gnupg-ring:/etc/apt/trusted.gpg.d/scalasbt-release.gpg --import && \
    chmod 644 /etc/apt/trusted.gpg.d/scalasbt-release.gpg && \
    apt-get update && \
    apt-get install -y sbt && \
    rm -rf /var/lib/apt/lists/*

# Copy build files
COPY build.sbt ./
COPY project/ ./project/

# Download dependencies first (better caching)
# Add retry logic for network issues
RUN sbt -Dsbt.ivy.home=/app/.ivy2 -Dsbt.global.base=/app/.sbt update || \
    (sleep 10 && sbt -Dsbt.ivy.home=/app/.ivy2 -Dsbt.global.base=/app/.sbt update) || \
    (sleep 30 && sbt -Dsbt.ivy.home=/app/.ivy2 -Dsbt.global.base=/app/.sbt update)

# Copy source code
COPY util/ ./util/
COPY domain/ ./domain/
COPY infrastructure/ ./infrastructure/
COPY adopter/ ./adopter/
COPY usecase/ ./usecase/
COPY controller/ ./controller/

# Build the application
RUN sbt -Dsbt.ivy.home=/app/.ivy2 -Dsbt.global.base=/app/.sbt assembly

# Stage 2: Runtime Stage
FROM ghcr.io/graalvm/graalvm-community:24

WORKDIR /app

# Install required tools
RUN apt-get update && \
    apt-get install -y curl netcat-traditional && \
    rm -rf /var/lib/apt/lists/*

# Copy the assembled JAR from build stage
COPY --from=build /app/controller/target/scala-3.7.1/controller-assembly-*.jar app.jar

# Copy wait script for database startup
COPY containers/wait-for-services.sh ./wait-for-services.sh
RUN chmod +x wait-for-services.sh

# Create non-root user
RUN groupadd -r handybookshelf && useradd -r -g handybookshelf handybookshelf
RUN chown -R handybookshelf:handybookshelf /app
USER handybookshelf

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/health || exit 1

# Start command with wait for dependencies
CMD ["./wait-for-services.sh", "java", "-Xmx512m", "-Xms256m", "-jar", "app.jar"]