-- Droits nécessaires au connecteur Debezium MySQL pour lire le binlog.
-- Exécuté automatiquement au premier démarrage du container (docker-entrypoint-initdb.d).
GRANT REPLICATION SLAVE, REPLICATION CLIENT, SELECT, RELOAD ON *.* TO 'devuser'@'%';
FLUSH PRIVILEGES;
