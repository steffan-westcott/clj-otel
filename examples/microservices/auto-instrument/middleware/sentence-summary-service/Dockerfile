FROM eclipse-temurin:17-alpine
EXPOSE 8080
COPY target/sentence-summary-service-standalone.jar /service.jar
COPY target/opentelemetry-javaagent.jar /opentelemetry-javaagent.jar
ENTRYPOINT ["java", \
            "-javaagent:/opentelemetry-javaagent.jar", \
            "-Dotel.resource.attributes=service.name=sentence-summary-service", \
            "-Dclojure.tools.logging.factory=clojure.tools.logging.impl/log4j2-factory", \
            "-Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager", \
            "-jar", \
            "/service.jar"]