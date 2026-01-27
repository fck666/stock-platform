FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY backend-api backend-api
RUN cd backend-api && ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre-jammy

RUN apt-get update && apt-get install -y --no-install-recommends python3 python3-venv python3-pip ca-certificates && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=build /workspace/backend-api/target/*.jar /app/app.jar
COPY data-collector /opt/data-collector

RUN python3 -m venv /opt/data-collector/venv \
  && /opt/data-collector/venv/bin/pip install --no-cache-dir -r /opt/data-collector/requirements.txt

ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080

ENTRYPOINT ["java","-jar","/app/app.jar"]

