FROM example.clj-otel/library-base-deps
LABEL example.clj-otel=library-base
WORKDIR /build
COPY . /build
