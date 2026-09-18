-- MYSQL_DATABASE (image officielle) ne crée que "maBase" : la base
-- "sso" est créée ici, sur le même serveur MySQL, avec les droits
-- pour devuser (database-per-service, un seul moteur).
CREATE DATABASE IF NOT EXISTS sso;
GRANT ALL PRIVILEGES ON sso.* TO 'devuser'@'%';
FLUSH PRIVILEGES;
