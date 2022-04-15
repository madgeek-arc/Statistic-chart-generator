# syntax=docker/dockerfile:1
FROM openjdk:11

# find .war file inside target folder
ARG FILE=target/*.war

COPY ${FILE} stats-tool.war
ENTRYPOINT ["java","-jar","/stats-tool.war"]
