#########################################################
# builds docker images
#########################################################

docker build -f DockerfileKafkaBase -t maxant/kafkabase .

docker build -f DockerfileZookeeper -t maxant/zookeeper .

docker build -f DockerfileKafkaBroker -t maxant/kafka .

mvn -pl web clean install

mvn -pl claims clean install

mvn -pl tasks clean install

cd ui
npm install
cd ..
