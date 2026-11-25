# One Dockerfile for every service: docker build --build-arg MODULE=ledger-service .
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY ledger-service/pom.xml ledger-service/
COPY notification-service/pom.xml notification-service/
COPY api-gateway/pom.xml api-gateway/
ARG MODULE
# Dependencies are cached in their own layer, so code changes do not re-download them
RUN ./mvnw -B -q -pl ${MODULE} -am dependency:go-offline
COPY . .
RUN ./mvnw -B -q -pl ${MODULE} -am package -DskipTests \
 && java -Djarmode=tools -jar ${MODULE}/target/${MODULE}-1.0.0.jar extract --layers --launcher --destination /extracted

# Runtime: JRE only, layered jar, non-root user
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
