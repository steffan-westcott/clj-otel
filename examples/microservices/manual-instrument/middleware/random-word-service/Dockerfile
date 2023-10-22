FROM example.clj-otel/microservices-base AS builder
RUN mkdir -p /build/examples/microservices/manual-instrument/middleware/random-word-service
WORKDIR /build
COPY . /build/examples/microservices/manual-instrument/middleware/random-word-service
RUN clojure -T:build uberjar :project '"random-word-service"'
FROM eclipse-temurin:21-alpine
LABEL example.clj-otel=random-word-service
EXPOSE 8081
COPY --from=builder \
     /build/examples/microservices/manual-instrument/middleware/random-word-service/target/random-word-service-standalone.jar \
     /service.jar
ENTRYPOINT ["java", \
            "-Dotel.resource.attributes=service.name=random-word-service", \
            "-Dclojure.tools.logging.factory=clojure.tools.logging.impl/log4j2-factory", \
            "-Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager", \
            "-jar", \
            "/service.jar"]
HEALTHCHECK --start-period=60s --interval=5s --timeout=1s --retries=2 \
            CMD wget --spider -q http://localhost:8081/ping || exit 1
