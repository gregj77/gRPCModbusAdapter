FROM adoptopenjdk:11-jre-hotspot
COPY target/dependencies/ ./
RUN true
COPY target/snapshot-dependencies/ ./
RUN true
COPY target/spring-boot-loader/ ./
RUN true
COPY target/application/ ./
RUN true
ENTRYPOINT ["java", "org.springframework.boot.loader.JarLauncher"]