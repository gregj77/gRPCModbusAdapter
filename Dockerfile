FROM adoptopenjdk:11-jre-hotspot
COPY target/dependencies/ ./
COPY target/snapshot-dependencies/ ./
COPY target/spring-boot-loader/ ./
COPY target/application/ ./
ENTRYPOINT ["java", "org.springframework.boot.loader.JarLauncher"]