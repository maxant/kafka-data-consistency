# cd objects
# export JAVA_HOME=/usr/local/graalvm-ce-java11-19.3.2
# export GRAALVM_HOME=/usr/local/graalvm-ce-java11-19.3.2/
# export PATH=/usr/local/apache-maven-3.6.3/bin:$PATH
# mvn clean package -Pnative -Dquarkus.native.container-build=true
# ./target/objects-1.0-SNAPSHOT-runner -Xmx32m -Dquarkus.profile=dev -Dquarkus.http.port=8086
#
# OR, old stuff, that didnt work because the docker image doesnt contain docker which quarkus build uses
# build jar: docker run --name jdkmvn -it --rm -v  $(pwd):/project -v /root/.m2/repository:/root/.m2/repository maxant/jdkmvn mvn clean package
# test jar: docker run -it --rm -p 8086:8086 -v $(pwd):/app openjdk:11.0.7-slim-buster java -jar /app/target/objects-1.0-SNAPSHOT-runner.jar
# build exe: docker run --name jdkmvn -it --rm -v  $(pwd):/project -v /root/.m2/repository:/root/.m2/repository maxant/jdkmvn mvn clean package -Pnative -Dquarkus.native.container-build=true
# test exe: docker run -it --rm -p 8086:8086 -v $(pwd):/app openjdk:11.0.7-slim-buster ./objects-1.0-SNAPSHOT-runner
# build image: docker build -f src/main/docker/Dockerfile -t maxant/kdc-objects .
# run: docker run -it --rm -p 8086:8086 maxant/kdc-objects

#FROM openjdk:11.0.7-slim-buster
FROM debian:buster-slim

#COPY target/lib/* /deployments/lib/
#COPY target/*-runner.jar /deployments/app.jar
COPY target/*-runner /deployments/app

EXPOSE 8086

#CMD java -jar /deployments/app.jar
CMD ["./app", "-Dquarkus.http.host=0.0.0.0", "-Xmx32m"]
# -Dquarkus.profile=dev -Dquarkus.http.port=8086
