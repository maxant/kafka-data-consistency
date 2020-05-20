# docker run --name jdkmvn -it -v  $(pwd):/project -v /root/.m2/repository:/root/.m2/repository maxant/jdkmvn mvn clean install
# docker run --name jdkmvn -it -v  $(pwd)/objects:/project -v /root/.m2/repository:/root/.m2/repository maxant/jdkmvn mvn clean install
FROM openjdk:11.0.7-slim-buster

COPY objects/target/objects-1.0-SNAPSHOT-runner.jar objects-service.jar

# TODO jmx port EXPOSE 8086
EXPOSE 8086

CMD java -jar objects-service.jar
