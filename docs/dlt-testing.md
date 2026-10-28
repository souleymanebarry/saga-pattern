# 🧪 Tester les Dead Letter Topics avec la console Kafka

Ce guide vérifie à la main que les messages en échec consommés par `orders-service` partent
bien en DLT au lieu de bloquer la partition, dans les trois cas prévus :

| Cas | Comment le provoquer | Tentatives | `dlt-delivery-attempts` | `kafka_dlt-exception-cause-fqcn` |
| --- | --- | --- | --- | --- |
| 1. Poison pill | texte brut au lieu d'Avro | 1 | `1` | `DeserializationException` |
| 2. Erreur non rejouable | `orderId` qui n'est pas un UUID | 1 | `1` | `IllegalArgumentException` |
| 3. Erreur rejouable | Postgres arrêté | 4 (≈ 2 min) | `4` | `CannotCreateTransactionException` |

Le fonctionnement des DLT est décrit dans [`saga-flow.md` § 6 bis](saga-flow.md#6-bis-dead-letter-topics-dlt).

---

## Prérequis

1. L'infra est démarrée et les connecteurs Debezium sont enregistrés :

   ```bash
   docker compose up -d
   ./infra/connectors/register.sh
   ```

   > ⚠ Si un Postgres est installé directement sur la machine, il occupe le port 5432 et le
   > conteneur `postgres` ne démarre pas. Les tâches Debezium passent alors en `FAILED`
   > (`UnknownHostException: postgres`). Arrêter le Postgres local
   > (`sudo systemctl stop postgresql`), ou exposer le conteneur sur un autre port
   > (`POSTGRES_PORT=5433 docker compose up -d`).

2. `orders-service` est lancé : ses consumers sont abonnés à `stock.reserved` et `stock.rejected`.

   ```bash
   mvn -pl orders-service -am install -DskipTests
   cd orders-service && mvn spring-boot:run
   ```

---

## 1. Ouvrir un shell dans le conteneur Kafka

Les outils `kafka-console-producer` et `kafka-console-consumer` sont fournis par l'image
`confluentinc/cp-kafka` : inutile de les installer sur la machine.

```bash
docker exec -it broker-kafka bash
```

Dans le conteneur, on utilise le listener interne `broker-kafka:29092`.

> 💡 Les commandes ci-dessous tiennent sur **une seule ligne**. Si on les découpe avec `\`, le
> `\` doit être le tout dernier caractère de la ligne : un espace derrière (fréquent au
> copier-coller) coupe la commande et donne `Missing required option(s) [bootstrap-server]`
> puis `--topic: command not found`.

---

## 2. Cas 1 : envoyer un poison pill sur `stock.reserved`

```bash
kafka-console-producer --bootstrap-server broker-kafka:29092 --topic stock.reserved --property parse.key=true --property key.separator=:
```

Au prompt `>`, saisir un message au format `clé:valeur`, qui n'est pas de l'Avro, puis `Ctrl+C` :

```
test-key:ceci-nest-pas-de-lavro
```

---

## 3. Vérifier les logs d'`orders-service`

Une erreur de désérialisation n'est pas rejouable : un seul échec, puis envoi direct en DLT,
**sans** les 3 rejeux avec backoff.

```
WARN  ... KafkaDeadLetterConfig : ⚠ Kafka delivery failed | topic=stock.reserved | partition=2 | offset=0 | attempt=1/4 | error=Listener failed
ERROR ... KafkaDeadLetterConfig : ☠ Message sent to DLT stock.reserved.dlt | source: topic=stock.reserved | partition=2 | offset=0 | key=test-key
```

La consommation reprend ensuite normalement : le message suivant de la partition est traité.

---

## 4. Lire la DLT

Toujours dans le conteneur :

```bash
kafka-console-consumer --bootstrap-server broker-kafka:29092 --topic stock.reserved.dlt --from-beginning --property print.key=true --property print.headers=true
```

On doit retrouver le message d'origine, avec la même clé et les mêmes octets, accompagné des
headers ajoutés par Spring :

| Header | Contenu |
| --- | --- |
| `kafka_dlt-original-topic` | `stock.reserved` |
| `kafka_dlt-original-partition` / `kafka_dlt-original-offset` | position du message source |
| `kafka_dlt-exception-fqcn` | classe de l'exception (`DeserializationException`…) |
| `kafka_dlt-exception-cause-fqcn` | classe de l'exception d'origine (voir ci-dessous) |
| `kafka_dlt-exception-message` / `kafka_dlt-exception-stacktrace` | diagnostic |
| `dlt-delivery-attempts` | nombre de tentatives avant la DLT : `1` ici (pas de rejeu), `4` pour une erreur rejouable |

Pour un poison pill, l'erreur survient à la désérialisation, avant le listener : `-fqcn` et
`-cause-fqcn` valent tous deux `DeserializationException`. Pour une erreur levée **dans** le
listener (cas 2 et 3), Spring l'enveloppe dans une `ListenerExecutionFailedException` : c'est
alors `kafka_dlt-exception-cause-fqcn` qu'il faut lire pour connaître la vraie cause.

`Ctrl+C` pour quitter le consumer, puis `exit` pour sortir du conteneur.

On peut faire la même chose dans Conduktor (http://localhost:8088) : produire sur
`stock.reserved`, puis consulter `stock.reserved.dlt`.

> ⚠ Les headers ajoutés par Spring sont sur la **copie** publiée dans `stock.reserved.dlt`,
> pas sur le message d'origine dans `stock.reserved`. Les headers `original-partition`,
> `original-offset` et `original-timestamp` sont des entiers binaires : ils s'affichent comme
> des caractères illisibles, c'est normal.

---

## 5. Envoyer un message Avro valide (cas 2 et 3)

Pour les cas 2 et 3, le message doit être un **vrai** `StockReservedEvent` en Avro, sinon il
partirait en DLT dès la désérialisation (cas 1). On utilise `kafka-avro-console-producer`,
fourni par l'image `confluentinc/cp-schema-registry` :

```bash
docker exec -it schema-registry bash
```

Récupérer l'id du schéma enregistré pour `stock.reserved` (depuis la machine) :

```bash
curl -s localhost:8081/subjects/stock.reserved-value/versions/latest | jq .id
```

Puis, dans le conteneur, lancer le producer en remplaçant `2` par cet id :

```bash
kafka-avro-console-producer --bootstrap-server broker-kafka:29092 --topic stock.reserved --property schema.registry.url=http://localhost:8081 --property value.schema.id=2 --property avro.use.logical.type.converters=true --property parse.key=true --property "key.separator=|" --property key.serializer=org.apache.kafka.common.serialization.StringSerializer
```

- `value.schema.id` : réutilise le schéma déjà enregistré au lieu d'en enregistrer un nouveau.
- `avro.use.logical.type.converters=true` : **obligatoire**. `reservedAt` est un
  `timestamp-millis` ; sans cette option, le producer convertit la valeur en `Instant` à la
  lecture du JSON mais le sérialiseur attend un `long`, et il s'arrête sur
  `ClassCastException: value ... (a java.time.Instant) cannot be cast to expected type long`.
- `key.separator=|` : la valeur JSON contient des `:`, on sépare donc la clé avec `|`.

> 💡 Chaque ligne saisie est un message. Si le JSON est coupé en deux lignes au copier-coller
> (retour à la ligne automatique du terminal), le producer reçoit un JSON tronqué et s'arrête
> sur `JsonEOFException: Unexpected end-of-input`. Vérifier que le message tient sur **une
> seule ligne** avant de valider. En cas d'erreur, le producer s'arrête : il faut le relancer.

---

## 6. Cas 2 : erreur non rejouable (`IllegalArgumentException`)

Postgres doit être **démarré**. Au prompt `>` du producer Avro :

```
iae-test|{"eventId":"evt-iae-1","orderId":"abc","items":[{"sku":"SKU-PIXEL9","quantity":1}],"reservedAt":1761700000000,"version":"v1"}
```

`"abc"` n'est pas un UUID : `UUID.fromString` lève une `IllegalArgumentException` dans
`StockEventListener.onStockReserved`. Rejouer ne changerait rien, le message part donc en DLT
dès le premier échec :

```
WARN  ... KafkaDeadLetterConfig : ⚠ Kafka delivery failed | topic=stock.reserved | partition=1 | offset=1 | attempt=1/4 | error=Listener method '... StockEventListener.onStockReserved(...)' threw exception
ERROR ... KafkaDeadLetterConfig : ☠ Message sent to DLT stock.reserved.dlt | source: topic=stock.reserved | partition=1 | offset=1 | key=iae-test
```

Dans `stock.reserved.dlt` : l'événement Avro d'origine (lisible dans Conduktor), avec
`dlt-delivery-attempts = 1` et `kafka_dlt-exception-cause-fqcn = java.lang.IllegalArgumentException`.

---

## 7. Cas 3 : erreur rejouable (Postgres arrêté)

Arrêter Postgres (depuis la machine) :

```bash
docker stop postgres
```

Au prompt `>` du producer Avro, envoyer un message avec un UUID valide. La commande n'a pas
besoin d'exister : l'échec survient à l'ouverture de la transaction, avant toute lecture en base.

```
retry-test|{"eventId":"e2","orderId":"11111111-1111-1111-1111-111111111111","items":[],"reservedAt":0,"version":"v1"}
```

Le message est rejoué 3 fois, puis envoyé en DLT :

```
WARN  ... 16:42:12 ... attempt=1/4 | error=Listener method '... StockEventListener.onStockReserved(...)' threw exception
WARN  ... 16:42:43 ... attempt=2/4 | ...
WARN  ... 16:43:15 ... attempt=3/4 | ...
WARN  ... 16:43:49 ... attempt=4/4 | ...
ERROR ... 16:43:49 ... ☠ Message sent to DLT stock.reserved.dlt | source: topic=stock.reserved | partition=2 | offset=2 | key=retry-test
```

⏱ Compter **environ 2 minutes**. Chaque tentative attend d'abord jusqu'à 30 s une connexion
au pool Hikari (délai par défaut), puis s'ajoutent les pauses de 1 s, 2 s et 4 s du backoff :
les échecs sont donc espacés d'environ 31 s, et non de 1/2/4 s.

Dans `stock.reserved.dlt` : `dlt-delivery-attempts = 4` et
`kafka_dlt-exception-cause-fqcn = org.springframework.transaction.CannotCreateTransactionException`
(cause racine dans la stacktrace : `java.net.ConnectException`).

> ⚠ Conduktor peut afficher les headers de ce message comme vides, probablement parce que la
> stacktrace fait environ 10 Ko et contient des caractères accentués (`Connexion refusée`).
> Les headers sont pourtant
> bien présents ; on le vérifie avec la console, dans le conteneur `broker-kafka` (remplacer
> `3` par l'offset du message dans la DLT) :
>
> ```bash
> kafka-console-consumer --bootstrap-server broker-kafka:29092 --topic stock.reserved.dlt --partition 0 --offset 3 --max-messages 1 --property print.key=true --property print.headers=true
> ```

**Remise en état** : redémarrer Postgres, puis relancer les tâches Debezium, qui ont échoué
pendant la coupure (voir ci-dessous).

```bash
docker start postgres
```

---

## Vérifier l'état des connecteurs Debezium

```bash
curl -s "localhost:8083/connectors?expand=status" | jq
```

Une tâche en `FAILED` ne redémarre pas toute seule, même une fois la cause corrigée. On la
relance avec :

```bash
for c in orders-outbox-connector stock-outbox-connector; do curl -X POST "localhost:8083/connectors/$c/restart?includeTasks=true&onlyFailed=true"; done
```
