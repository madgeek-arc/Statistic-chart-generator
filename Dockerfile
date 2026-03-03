FROM eclipse-temurin:17-jre

WORKDIR /usr/local/app

COPY ./Application/target/Statistic-chart-generator-Application*.war ./stats-api.war

RUN mkdir -p config logs

VOLUME ["/usr/local/app/config", "/usr/local/app/logs"]

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/usr/local/app/stats-api.war", "--spring.config.location=file:/usr/local/app/config/"]
