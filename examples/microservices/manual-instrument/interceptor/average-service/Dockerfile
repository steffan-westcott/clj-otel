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