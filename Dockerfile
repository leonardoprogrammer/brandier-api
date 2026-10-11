FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S brandier && adduser -S brandier -G brandier
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
USER brandier
EXPOSE 8080 9090
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD wget --spider -q http://localhost:9090/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
