FROM eclipse-temurin:17-alpine
EXPOSE 8080
COPY target/average-service-standalone.jar /service.jar
ENTRYPOINT ["java", \
            "-Dotel.resource.attributes=service.name=average-service", \
            "-Dotel.java.global-autoconfigure.enabled=true", \
            "-Dclojure.tools.logging.factory=clojure.tools.logging.impl/log4j2-factory", \
            "-Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager", \
            "-jar", \
            "/service.jar"]
HEALTHCHECK --start-period=60s --interval=5s --timeout=1s --retries=2 \
            CMD wget --spider -q http://localhost:8080/ping || exit 1
