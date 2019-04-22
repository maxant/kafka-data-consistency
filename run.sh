#########################################################
# runs images. ensure you call build.sh first!
#########################################################
#
# NOTE: no volumes are used, so restarting all images
#       causes data and topic loss!
#########################################################
#
# TODO at the moment, if you want to change the number
#      of brokers, you need to do it here and down below
#      it would be nice to be able to set a param just once
#########################################################

#########################################################
# stop kafka brokers
for id in {1..2}
do
    p=909$id:9092

    if [ `docker ps --all | grep kafka_$id | wc -l` -ge 1 ]
      then
        if [[ "`docker inspect -f {{.State.Status}} kafka_$id`" == "running" ]]
          then
            echo killing kafka_$id...
            docker kill kafka_$id
        else
            echo kafka_$id not running
        fi
        echo removing kafka_$id container...
        docker rm kafka_$id
    else
        echo kafka_$id not found
    fi

done


#########################################################
# zookeeper

if [ `docker ps --all | grep zookeeper | wc -l` -ge 1 ]
  then
    if [[ "`docker inspect -f {{.State.Status}} zookeeper`" == "running" ]]
      then
        echo killing zookeeper...
        docker kill zookeeper
    else
        echo zookeeper not running
    fi
    echo removing zookeeper container...
    docker rm zookeeper
else
    echo zookeeper not found
fi

echo starting zookeeper...
docker run --name zookeeper -p 2181:2181 maxant/zookeeper >/dev/null 2>&1 &

#"INFO binding to port 0.0.0.0/0.0.0.0:2181"
counter=0
until [ $counter -ge 1 ]
do
    echo waiting for zookeeper...
    sleep 0.5;
    counter=`docker logs zookeeper | grep binding | wc -l`
done

zookeeperhost=$(docker inspect -f {{.NetworkSettings.IPAddress}} zookeeper)
echo zookeeper is up: $zookeeperhost

#########################################################
# get ip address from docker0 interface and grep just the ip address
dockerhost=$(ip -4 addr show docker0 | grep -Po 'inet \K[\d.]+')
echo docker host is: $dockerhost

#########################################################
# start kafka brokers

kafkahosts=
for id in {1..2}
do
    p=909$id:9092

    echo starting kafka_$id...

    docker run --name kafka_$id -p $p -e "ID=$id" -e "DOCKER_HOST=$dockerhost" -e "ZOOKEEPER_HOST=$zookeeperhost" maxant/kafka >/dev/null 2>&1 &

    sleep 0.5;

    kafkahost=$(docker inspect -f {{.NetworkSettings.IPAddress}} kafka_$id)
    echo kafka_$id is up: $kafkahost
    kafkahosts=$kafkahost,$kafkahosts
done


counter=0
until [ $counter -ge 1 ]
do
    echo waiting for kafka_1...
    sleep 0.5;
    counter=`docker logs kafka_1 | grep "started (kafka.server.KafkaServer)" | wc -l`
done

echo creating topic
kafka_2.11-2.1.1/bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 2 --partitions 4 --topic claim-create-command
kafka_2.11-2.1.1/bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 2 --partitions 4 --topic task-create-command
kafka_2.11-2.1.1/bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 2 --partitions 4 --topic claim-created-event
kafka_2.11-2.1.1/bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 2 --partitions 4 --topic task-created-event
echo topics created
echo ""
echo COMPLETED. Kafka boostrap servers: $kafkahosts

ps ax | grep web-microbundle | grep -v grep | awk '{print $1}' | xargs kill
ps ax | grep tasks-microbundle | grep -v grep | awk '{print $1}' | xargs kill
ps ax | grep claims-microbundle | grep -v grep | awk '{print $1}' | xargs kill

java -Xmx64M -Xms64M -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8787 -Dkafka.bootstrap.servers=172.17.0.4:9092,172.17.0.3:9092 -jar web/target/web-microbundle.jar --port 8080 &
java -Xmx64M -Xms64M -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8788 -Dkafka.bootstrap.servers=172.17.0.4:9092,172.17.0.3:9092 -jar claims/target/claims-microbundle.jar --port 8081 &
java -Xmx64M -Xms64M -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8789 -Dkafka.bootstrap.servers=172.17.0.4:9092,172.17.0.3:9092 -jar tasks/target/tasks-microbundle.jar --port 8082 &


ps -Af | grep micro

ps ax | grep "http-server.*8083" | grep -v grep | awk '{print $1}' | xargs kill

cd ui
node node_modules/http-server/bin/http-server -p 8083 &

# TODO add ports to kafka brokers list
# browser: http://localhost:8080/web/
#
# view topics: ./kafka-console-consumer.sh  --topic claim-create --from-beginning --bootstrap-server 172.17.0.3:9092
#              ./kafka-console-consumer.sh  --topic task-create  --from-beginning --bootstrap-server 172.17.0.3:9092
#
# create a claim:
# curl -X POST   http://localhost:8081/claims/rest/claims/create   -H 'Content-Type: application/json'   -H 'cache-control: no-cache'   -d '{"description" :"asdf", "customerId": "C12345678"}'
