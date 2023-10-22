FROM example.clj-otel/microservices-base AS builder
RUN mkdir -p /build/examples/microservices/manual-instrument/middleware/puzzle-load-gen
WORKDIR /build
COPY . /build/examples/microservices/manual-instrument/middleware/puzzle-load-gen
RUN clojure -T:build uberjar :project '"puzzle-load-gen"'
FROM eclipse-temurin:21-alpine
LABEL example.clj-otel=puzzle-load-gen
COPY --from=builder \
     /build/examples/microservices/manual-instrument/middleware/puzzle-load-gen/target/puzzle-load-gen-standalone.jar \
     /load-gen.jar
ENTRYPOINT ["java", \
            "-Dclojure.tools.logging.factory=clojure.tools.logging.impl/log4j2-factory", \
            "-Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager", \
            "-jar", \
            "/load-gen.jar"]