FROM ubuntu:22.04

RUN apt update && apt upgrade
RUN apt install -y maven openjdk-17-jdk git

WORKDIR /usr/local/app

COPY ./Application/target/Statistic-chart-generator-Application*.war ./stats-api.war

ENTRYPOINT ["java","-jar","/usr/local/app/stats-api.war", "--spring.config.location=file:/usr/local/app/config/"]
