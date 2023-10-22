FROM example.clj-otel/microservices-base AS builder
RUN mkdir -p /build/examples/microservices/auto-instrument/middleware/sentence-summary-load-gen
WORKDIR /build
COPY . /build/examples/microservices/auto-instrument/middleware/sentence-summary-load-gen
RUN clojure -T:build uberjar :project '"sentence-summary-load-gen"'
FROM eclipse-temurin:21-alpine
LABEL example.clj-otel=sentence-summary-load-gen
COPY --from=builder \
     /build/examples/microservices/auto-instrument/middleware/sentence-summary-load-gen/target/sentence-summary-load-gen-standalone.jar \
     /load-gen.jar
ENTRYPOINT ["java", \
            "-Dclojure.tools.logging.factory=clojure.tools.logging.impl/log4j2-factory", \
            "-Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager", \
            "-jar", \
            "/load-gen.jar"]