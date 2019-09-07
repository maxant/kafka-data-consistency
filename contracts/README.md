
runs using quarkus

persistence is in mysql

create DB:

    docker run -it --rm mysql mysql -h maxant.ch --port 30300 -u root -psecret -e "CREATE DATABASE contracts CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"

Conncecting: 

    docker run -it --rm mysql mysql -h maxant.ch --port 30300 -u root -psecret contracts

# TODO

- jsonb => setVersion on Contract
- @QuarkusTest
