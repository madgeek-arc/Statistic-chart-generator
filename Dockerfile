FROM ubuntu:22.04

RUN apt update
RUN apt install -y maven openjdk-8-jdk git

RUN update-java-alternatives --set java-1.8.0-openjdk-amd64

workdir /usr/local/app

COPY pom.xml .
COPY ./Application/pom.xml ./Application/pom.xml
COPY ./ChartDataFormatter/pom.xml ./ChartDataFormatter/pom.xml
COPY ./DBAccess/pom.xml ./DBAccess/pom.xml

RUN mvn dependency:go-offline

COPY ./Application/src ./Application/src
COPY ./ChartDataFormatter/src ./ChartDataFormatter/src
COPY ./DBAccess/src ./DBAccess/src

RUN mvn clean package  -DskipTests

RUN git clone -c http.sslVerify=false https://code-repo.d4science.org/antonis.lempesis/stats-tool-configuration.git
RUN mkdir -p /var/lib/tomcat8/lib/statsConfig
RUN cp /usr/local/app/stats-tool-configuration/dev/* /var/lib/tomcat8/lib/statsConfig

ENTRYPOINT ["java","-jar","Application/target/Statistic-chart-generator-Application-0.0.1-SNAPSHOT.war","--server.port=8180","--spring.config.location=file:/var/lib/tomcat8/lib/statsConfig/", "--spring.redis.host=esperos.di.uoa.gr"]
