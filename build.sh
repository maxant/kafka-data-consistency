#########################################################
# builds docker images
#########################################################

docker build -f DockerfileKafkaBase -t maxant/kafkabase .

docker build -f DockerfileZookeeper -t maxant/zookeeper .

docker build -f DockerfileKafkaBroker -t maxant/kafka .

docker build -f DockerfileElasticsearch -t maxant/elasticsearch .

docker build -f DockerfileNeo4j -t maxant/neo4j .

docker build -f DockerfileKibana -t maxant/kibana .

docker build -f DockerfileElasticApmServer -t maxant/elasticapmserver .

docker build -f DockerfileMysql -t maxant/mysql .

ps ax | grep web-microbundle | grep -v grep | awk '{print $1}' | xargs kill

ps ax | grep tasks-microbundle | grep -v grep | awk '{print $1}' | xargs kill

ps ax | grep claims-microbundle | grep -v grep | awk '{print $1}' | xargs kill

mvn -pl web clean install && \
mvn -pl claims clean install && \
mvn -pl tasks clean install

cd ui
npm install
cd ..
