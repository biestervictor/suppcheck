# Verwende ein Maven-Image, um die Anwendung zu bauen
FROM maven:3.9.4-eclipse-temurin-21 AS build
WORKDIR /app

# Kopiere die Maven-Projektdateien
COPY pom.xml .
COPY src ./src

# Baue die Anwendung
RUN mvn clean package -DskipTests

# Verwende ein leichtgewichtiges JDK-Image, um die Anwendung auszuführen
FROM eclipse-temurin:21-jre
WORKDIR /app

# Zeitzone setzen
RUN apt-get update && apt-get install -y --no-install-recommends tzdata \
    && ln -sf /usr/share/zoneinfo/Europe/Berlin /etc/localtime \
    && echo "Europe/Berlin" > /etc/timezone \
    && rm -rf /var/lib/apt/lists/*
ENV TZ=Europe/Berlin

# Kopiere das erstellte JAR-File aus dem Build-Container
COPY --from=build /app/target/*.jar app.jar

# Setze das Spring-Profil auf docker
ENV SPRING_PROFILES_ACTIVE=docker

# Exponiere den Standardport der Spring-Boot-Anwendung
EXPOSE 8080

# Starte die Anwendung
ENTRYPOINT ["java", "-jar", "app.jar"]