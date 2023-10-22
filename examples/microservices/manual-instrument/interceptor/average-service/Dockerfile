FROM example.clj-otel/microservices-base AS builder
RUN mkdir -p /build/examples/microservices/manual-instrument/interceptor/average-service
WORKDIR /build
COPY . /build/examples/microservices/manual-instrument/interceptor/average-service
RUN clojure -T:build uberjar :project '"average-service"'
FROM eclipse-temurin:21-alpine
LABEL example.clj-otel=average-service
EXPOSE 8080
COPY --from=builder \
     /build/examples/microservices/manual-instrument/interceptor/average-service/target/average-service-standalone.jar \
     /service.jar
ENTRYPOINT ["java", \
            "-Dotel.resource.attributes=service.name=average-service", \
            "-Dclojure.tools.logging.factory=clojure.tools.logging.impl/log4j2-factory", \
            "-Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager", \
            "-jar", \
            "/service.jar"]
HEALTHCHECK --start-period=60s --interval=5s --timeout=1s --retries=2 \
            CMD wget --spider -q http://localhost:8080/ping || exit 1
