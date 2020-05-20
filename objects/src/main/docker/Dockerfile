# docker run --name jdkmvn -it -v  $(pwd):/project -v /root/.m2/repository:/root/.m2/repository maxant/jdkmvn mvn clean install
# docker build -f src/main/docker/Dockerfile -t maxant/kdc-objects .
FROM openjdk:11.0.7-slim-buster

COPY target/objects-1.0-SNAPSHOT-runner.jar objects-service.jar

# TODO jmx port EXPOSE 8086
EXPOSE 8086

CMD java -jar objects-service.jar
