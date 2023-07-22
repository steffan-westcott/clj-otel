FROM eclipse-temurin:17-alpine
COPY target/solar-system-load-gen-standalone.jar /load-gen.jar
ENTRYPOINT ["java", \
            "-Dclojure.tools.logging.factory=clojure.tools.logging.impl/log4j2-factory", \
            "-Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager", \
            "-jar", \
            "/load-gen.jar"]