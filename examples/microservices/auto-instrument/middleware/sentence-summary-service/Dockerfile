FROM example.clj-otel/microservices-base AS builder
RUN mkdir -p /build/examples/microservices/auto-instrument/middleware/sentence-summary-service
WORKDIR /build
COPY . /build/examples/microservices/auto-instrument/middleware/sentence-summary-service
RUN clojure -T:build uberjar :project '"sentence-summary-service"'
FROM eclipse-temurin:21-alpine
LABEL example.clj-otel=sentence-summary-service
EXPOSE 8080
COPY --from=builder \
     /build/examples/microservices/auto-instrument/middleware/sentence-summary-service/target/sentence-summary-service-standalone.jar \
     /service.jar
COPY --from=builder \
     /build/examples/opentelemetry-javaagent.jar \
     /opentelemetry-javaagent.jar
ENTRYPOINT ["java", \
            "-javaagent:/opentelemetry-javaagent.jar", \
            "-Dotel.semconv-stability.opt-in=http", \
            "-Dotel.resource.attributes=service.name=sentence-summary-service", \
            "-Dclojure.tools.logging.factory=clojure.tools.logging.impl/log4j2-factory", \
            "-Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager", \
            "-jar", \
            "/service.jar"]
HEALTHCHECK --start-period=60s --interval=5s --timeout=1s --retries=2 \
            CMD wget --spider -q http://localhost:8080/ping || exit 1
