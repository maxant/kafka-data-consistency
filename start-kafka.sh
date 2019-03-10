#########################################################
# appends properties to end of config file
# and then starts kafka as normal
#########################################################

file=/kafka/config/server.properties

#new lines at end of file
echo  "" >>                 $file
echo  "" >>                 $file
echo  "# MAXANT CONFIG:" >> $file

echo $ZOOKEEPER_HOST zookeeper >> /etc/hosts
echo zookeeper.connect=zookeeper:2181 >> $file
echo broker.id=$ID >>                          $file
echo listeners=PLAINTEXT://:9092 >>            $file

#hat vo set advertised.listeners, otherwise it doesnt work when inside docker
ip=$(hostname -I)
#need to trim space at end of ip :-(
#ip=${ip::-1}
echo "own ip address: ::$ip::"
echo advertised.listeners=PLAINTEXT://${ip%?}:9092 >> $file

echo "log4j.rootLogger=DEBUG, stdout, kafkaAppender" >> /kafka/config/log4j.properties



# if you need to use the docker host, you could do it like this:
#echo zookeeper.connect=$DOCKER_HOST:2181 >>    $file

cat /kafka/config/server.properties

./bin/kafka-server-start.sh $file
