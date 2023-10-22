FROM example.clj-otel/microservices-base AS builder
RUN mkdir -p /build/examples/microservices/manual-instrument/middleware/puzzle-service
WORKDIR /build
COPY . /build/examples/microservices/manual-instrument/middleware/puzzle-service
RUN clojure -T:build uberjar :project '"puzzle-service"'
FROM eclipse-temurin:21-alpine
LABEL example.clj-otel=puzzle-service
EXPOSE 8080
COPY --from=builder \
     /build/examples/microservices/manual-instrument/middleware/puzzle-service/target/puzzle-service-standalone.jar \
     /service.jar
ENTRYPOINT ["java", \
            "-Dotel.resource.attributes=service.name=puzzle-service", \
            "-Dclojure.tools.logging.factory=clojure.tools.logging.impl/log4j2-factory", \
            "-Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager", \
            "-jar", \
            "/service.jar"]
HEALTHCHECK --start-period=60s --interval=5s --timeout=1s --retries=2 \
            CMD wget --spider -q http://localhost:8080/ping || exit 1
