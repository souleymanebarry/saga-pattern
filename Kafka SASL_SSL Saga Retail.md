saga-pattern · feature/kafka-security

# Sécuriser Kafka entre orders-service et stock-service

Plan de mise en place de SASL_SSL avec SCRAM-SHA-512 et des ACL par service, pour l'infra docker-compose actuelle (cp-kafka 7.6, ZooKeeper, Debezium 2.7).

## Ce qu'on met en place

TLS

### Chiffrer

Le trafic client ↔ broker est chiffré. Le client vérifie le certificat du broker.

SASL SCRAM-SHA-512

### Authentifier

Chaque service se connecte avec son propre compte : `User:orders-service`, `User:stock-service`…

ACL

### Autoriser

Chaque compte n'a accès qu'à ses topics et à son groupe de consommateurs. Tout le reste est refusé.

## Aujourd'hui : la saga via Kafka

Les services n'écrivent jamais directement dans Kafka : Debezium publie le contenu des tables outbox. En bleu : topics Kafka. En pointillés : Dead Letter Topics. Tout circule en PLAINTEXT, sans authentification.

## Cible : chaque client s'authentifie, le broker autorise

Tous les clients Kafka passent en SASL_SSL avec leur propre compte. Le broker applique trois contrôles dans l'ordre, puis donne accès aux topics selon les ACL.

## Plan de mise en place

Les étapes sont dans l'ordre d'exécution. Les valeurs des variables d'environnement sont à valider pendant la mise en place.

1

### Créer la PKI locale

Un script `infra/security/gen-certs.sh` génère une CA auto-signée, le keystore du broker et un truststore commun aux clients.

- Le certificat du broker doit couvrir `broker-kafka` (clients Docker) **et** `localhost` (orders-service et stock-service lancés depuis IntelliJ), dans le SAN.
- Les fichiers générés vont dans `infra/security/secrets/`, ajouté au `.gitignore`.

2

### Passer le broker en SASL_SSL

Dans `docker-compose.yml`, service `kafka` :

```
KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: INTERNAL:SASL_SSL,EXTERNAL:SASL_SSL
KAFKA_SASL_ENABLED_MECHANISMS: SCRAM-SHA-512
KAFKA_SASL_MECHANISM_INTER_BROKER_PROTOCOL: SCRAM-SHA-512
KAFKA_SSL_KEYSTORE_FILENAME: broker.keystore.p12
KAFKA_SSL_KEYSTORE_CREDENTIALS: keystore_creds
KAFKA_SSL_KEY_CREDENTIALS: key_creds
KAFKA_SSL_TRUSTSTORE_FILENAME: truststore.p12
KAFKA_SSL_TRUSTSTORE_CREDENTIALS: truststore_creds
KAFKA_OPTS: -Djava.security.auth.login.config=/etc/kafka/secrets/broker_jaas.conf
# volume : ./infra/security/secrets:/etc/kafka/secrets:ro
```

Couper PLAINTEXT casse tous les clients d'un coup. Variante plus douce : ajouter des listeners SASL_SSL sur de nouveaux ports à côté des listeners PLAINTEXT, migrer les clients un par un, puis supprimer PLAINTEXT.

3

### Créer un compte SCRAM par client

Un compte `admin` (super-utilisateur, utilisé aussi entre brokers), puis un compte par client : `orders-service`, `stock-service`, `kafka-connect`, `schema-registry`, `conduktor`.

```
kafka-configs --zookeeper zookeeper:2181 --alter --entity-type users --entity-name orders-service --add-config 'SCRAM-SHA-512=[password=${ORDERS_KAFKA_PASSWORD}]'
```

Avec ZooKeeper, les comptes sont stockés dans ZooKeeper : on peut donc créer `admin` avant le démarrage du broker. Un script `infra/security/create-users.sh` les crée tous.

4

### Activer les ACL

```
KAFKA_AUTHORIZER_CLASS_NAME: kafka.security.authorizer.AclAuthorizer
KAFKA_SUPER_USERS: User:admin
KAFKA_ALLOW_EVERYONE_IF_NO_ACL_FOUND: "false"
```

Par défaut, tout est refusé. Les droits de chaque compte sont donnés dans le tableau ci-dessous, par un script `infra/security/create-acls.sh` :

```
kafka-acls --bootstrap-server broker-kafka:29092 --command-config /etc/kafka/secrets/admin.properties --add --allow-principal User:orders-service --operation Read --topic stock.reserved --topic stock.rejected --group orders-service-group
```

5

### Configurer orders-service et stock-service

Dans `application.yml`, le mot de passe venant d'une variable d'environnement :

```
spring:
  kafka:
    security:
      protocol: SASL_SSL
    ssl:
      trust-store-location: file:${KAFKA_TRUSTSTORE}
      trust-store-password: ${KAFKA_TRUSTSTORE_PASSWORD}
    properties:
      sasl.mechanism: SCRAM-SHA-512
      sasl.jaas.config: >-
        org.apache.kafka.common.security.scram.ScramLoginModule required
        username="orders-service" password="${KAFKA_PASSWORD}";
```

**Seul changement de code.** `KafkaConsumerConfig` et `KafkaDeadLetterConfig` construisent leurs propriétés à la main à partir de `bootstrap-servers` : ils n'héritent donc pas de `spring.kafka.security.*`. Les faire partir de `KafkaProperties.buildConsumerProperties(…)` et `buildProducerProperties(…)`, dans les deux services. Sinon, les consumers et les producers DLT resteront en PLAINTEXT et seront rejetés.

Les tests `@EmbeddedKafka` ne changent pas : ils tournent sur un broker embarqué sans sécurité.

6

### Configurer Kafka Connect, Schema Registry et Conduktor

- **Kafka Connect (Debezium)** : la même config SASL_SSL trois fois, pour le worker, pour son producer (qui publie les événements outbox) et pour son consumer : `CONNECT_SECURITY_PROTOCOL`, `CONNECT_PRODUCER_SECURITY_PROTOCOL`, `CONNECT_CONSUMER_SECURITY_PROTOCOL`, et les `SASL_*` correspondants.
- **Schema Registry** : `SCHEMA_REGISTRY_KAFKASTORE_SECURITY_PROTOCOL: SASL_SSL`, le mécanisme, le JAAS et le truststore.
- **Conduktor** : `CDK_CLUSTERS_0_PROPERTIES` avec `security.protocol`, `sasl.mechanism` et `sasl.jaas.config`.

7

### Sortir les secrets du code

- En local : un fichier `.env` non commité, lu par docker-compose et par IntelliJ, plus un `.env.example` commité avec des valeurs factices.
- En prod : Vault ou les secrets Kubernetes, avec une rotation des mots de passe.

8

### Vérifier

- Une connexion sans identifiants est refusée (`SaslAuthenticationException`).
- orders-service ne peut pas écrire dans `order.placed` (`TopicAuthorizationException`) : seul Debezium le peut.
- La saga complète passe : `POST /orders` → `order.placed` → `stock.reserved` → commande en `CONFIRMED`.
- Les tâches Debezium sont en `RUNNING`, et le poison pill de `docs/dlt-testing.md` arrive toujours en DLT.
- Conduktor affiche les topics avec son propre compte.

## ACL par compte

Le principe du moindre privilège : chaque compte n'a que ce dont il a besoin. `Read` et `Write` donnent aussi `Describe`.

| Compte | Ressource | Opérations | Pourquoi |
| --- | --- | --- | --- |
| `User:orders-service` | topics `stock.reserved`, `stock.rejected` | Read | StockEventListener |
| topics `stock.reserved.dlt`, `stock.rejected.dlt` | Write | publication en DLT |  |
| groupe `orders-service-group` | Read | commit des offsets |  |
| `User:stock-service` | topic `order.placed` | Read | StockOrderListener |
| topic `order.placed.dlt` | Write | publication en DLT |  |
| groupe `stock-service-group` | Read | commit des offsets |  |
| `User:kafka-connect` | topics `order.placed`, `order.confirmed`, `order.failed`, `stock.reserved`, `stock.rejected` | Write | publication des outbox |
| topics `_connect-configs`, `_connect-offsets`, `_connect-status` | All | état interne de Connect |  |
| groupe `kafka-connect-group` | Read | coordination des workers |  |
| `User:schema-registry` | topic `_schemas` | All | stockage des schémas |
| groupe `schema-registry` | Read | élection du leader |  |
| `User:conduktor` | tous les topics et groupes | Read, Describe | consultation, en lecture seule |

Les services créent aujourd'hui leurs topics au démarrage (`NewTopic` dans `KafkaTopicsConfig`). Deux options : leur donner `Create` sur leurs topics, ou, comme en prod, faire créer les topics par `admin` dans un script et retirer ce droit aux services. La seconde est recommandée.

## Hors périmètre de SASL/SSL

### REST orders → catalog

HTTPS, plus un jeton OAuth2 (client credentials) ou du mTLS entre les services.

### API HTTP du Schema Registry

HTTPS et basic auth : SASL ne protège que son accès au topic `_schemas`.

### Postgres

TLS et un compte par service, à la place de `postgres / 1234` partagé. Debezium a besoin du droit de réplication.

### ZooKeeper

Reste en clair ici. À sécuriser avec SASL, ou à supprimer en passant Kafka en mode KRaft.