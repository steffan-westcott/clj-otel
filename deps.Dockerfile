FROM clojure:temurin-21-alpine
LABEL example.clj-otel=library-base-deps
RUN mkdir -p /build
WORKDIR /build
COPY . /build
RUN clojure -T:build fetch-deps :paths build/library-project-paths
