FROM example.clj-otel/microservices-base AS builder
RUN mkdir -p /build/examples/microservices/auto-instrument/middleware/word-length-service
WORKDIR /build
COPY . /build/examples/microservices/auto-instrument/middleware/word-length-service
RUN clojure -T:build uberjar :project '"word-length-service"'
FROM eclipse-temurin:21-alpine
LABEL example.clj-otel=word-length-service
EXPOSE 8081
COPY --from=builder \
     /build/examples/microservices/auto-instrument/middleware/word-length-service/target/word-length-service-standalone.jar \
     /service.jar
COPY --from=builder \
     /build/examples/opentelemetry-javaagent.jar \
     /opentelemetry-javaagent.jar
ENTRYPOINT ["java", \
            "-javaagent:/opentelemetry-javaagent.jar", \
            "-Dotel.resource.attributes=service.name=word-length-service", \
            "-Dclojure.tools.logging.factory=clojure.tools.logging.impl/log4j2-factory", \
            "-Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager", \
            "-jar", \
            "/service.jar"]
HEALTHCHECK --start-period=60s --interval=5s --timeout=1s --retries=2 \
            CMD wget --spider -q http://localhost:8081/ping || exit 1
