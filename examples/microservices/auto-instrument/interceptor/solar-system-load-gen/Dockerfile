FROM example.clj-otel/microservices-base AS builder
RUN mkdir -p /build/examples/microservices/auto-instrument/interceptor/solar-system-load-gen
WORKDIR /build
COPY . /build/examples/microservices/auto-instrument/interceptor/solar-system-load-gen
RUN clojure -T:build uberjar :project '"solar-system-load-gen"'
FROM eclipse-temurin:21-alpine
LABEL example.clj-otel=solar-system-load-gen
COPY --from=builder \
     /build/examples/microservices/auto-instrument/interceptor/solar-system-load-gen/target/solar-system-load-gen-standalone.jar \
     /load-gen.jar
ENTRYPOINT ["java", \
            "-Dclojure.tools.logging.factory=clojure.tools.logging.impl/log4j2-factory", \
            "-Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager", \
            "-jar", \
            "/load-gen.jar"]