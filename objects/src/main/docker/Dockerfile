# build jar: docker run --name jdkmvn -it --rm -v  $(pwd):/project -v /root/.m2/repository:/root/.m2/repository maxant/jdkmvn mvn clean install
# test: docker run -it --rm -p 8086:8086 -v $(pwd):/app openjdk:11.0.7-slim-buster java -jar /app/target/objects-1.0-SNAPSHOT-runner.jar
# build image: docker build -f src/main/docker/Dockerfile -t maxant/kdc-objects .
# run: docker run -it --rm -p 8086:8086 maxant/kdc-objects

FROM openjdk:11.0.7-slim-buster

COPY target/lib/* /deployments/lib/
COPY target/*-runner.jar /deployments/app.jar

# TODO jmx port EXPOSE 8086
EXPOSE 8086

CMD java -jar app.jar
