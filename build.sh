#########################################################
# builds docker images
#########################################################

docker build -f DockerfileKafkaBase -t maxant/kafkabase .

docker build -f DockerfileZookeeper -t maxant/zookeeper .

docker build -f DockerfileKafkaBroker -t maxant/kafka .

