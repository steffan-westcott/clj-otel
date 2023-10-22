FROM example.clj-otel/library-base
LABEL example.clj-otel=microservices-base
RUN mkdir -p /build/examples
WORKDIR /build
COPY . /build/examples
RUN clojure -T:build fetch-deps :paths build/microservices-project-paths
