# cd objects
# export JAVA_HOME=/usr/local/graalvm-ce-java11-19.3.2
# export GRAALVM_HOME=/usr/local/graalvm-ce-java11-19.3.2/
# export PATH=/usr/local/apache-maven-3.6.3/bin:$PATH
# mvn clean package -Pnative -Dquarkus.native.container-build=true
# ./target/objects-1.0-SNAPSHOT-runner -Xmx32m -Dquarkus.profile=dev -Dquarkus.http.port=8086
# docker build -f src/main/docker/Dockerfile -t maxant/kdc-objects .
# docker run -it --rm -p 8086:8086 maxant/kdc-objects

#FROM openjdk:11.0.7-slim-buster
FROM debian:buster-slim

#COPY target/lib/* /deployments/lib/
#COPY target/*-runner.jar /deployments/app.jar
# named with a unique name so that we can see it on the docker host when using top
COPY target/*-runner /deployments/kdc-objects

EXPOSE 8086

#CMD java -jar /deployments/app.jar
CMD ["/deployments/kdc-objects", "-Dquarkus.http.host=0.0.0.0", "-Xmx32m"]
# -Dquarkus.profile=dev -Dquarkus.http.port=8086
