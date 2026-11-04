# 🧪 Tester l'idempotence de la création de commande

Ce guide vérifie à la main que `POST /api/v1/orders` est idempotent : le client génère une clé
(UUID), l'envoie dans le header obligatoire `Idempotency-Key` et la réutilise s'il retente la
requête. Une clé déjà connue renvoie la commande existante, sans nouvelle commande, sans nouvel
évènement et sans nouvelle réservation de stock.

| # | Scénario | Résultat attendu |
| --- | --- | --- |
| 1 | Création nominale | `200`, commande `PENDING` puis `CONFIRMED`, 1 évènement `OrderPlacedEvent` |
| 2 | Rejeu avec la même clé | `200`, **même `orderId`**, aucune nouvelle ligne, stock réservé une seule fois |
| 3 | Header absent | `400`, aucune commande |
| 4 | Header vide | `400`, aucune commande |
| 5 | Même body, nouvelle clé | `200`, **nouvelle** commande (la clé identifie la tentative, pas le contenu) |
| 6 | Même clé, body différent | `200`, commande **d'origine** renvoyée, nouveau body ignoré ⚠ limite connue |
| 7 | Requêtes simultanées, même clé | `200` partout, **même `orderId`**, 1 seule commande |

Le fonctionnement est décrit dans [`saga-flow.md` § 5](saga-flow.md#5-garanties--invariants).

---

## Prérequis

1. L'infra est démarrée et les connecteurs Debezium sont enregistrés :

   ```bash
   docker compose up -d
   ./infra/connectors/register.sh
   ```

2. `catalog-service` (8085), `stock-service` et `orders-service` (8080) sont lancés.
   `orders-service` doit tourner avec le code qui exige le header `Idempotency-Key`
   (le redémarrer après un changement de branche).

3. Les SKU utilisés existent dans le catalogue. Un SKU inconnu fait échouer la création en `422`
   (`Product not found`, catalog-service répond `404`), indépendamment de l'idempotence.

   ```bash
   curl -s localhost:8085/api/v1/products/SKU-PS5SLIM | jq '{sku, price}'
   ```

   SKU disponibles : `SKU-PS5SLIM`, `SKU-IPH15P`, `SKU-MBAM3`, `SKU-NSWOLED`, `SKU-SGS24`,
   `SKU-XPS15`, `SKU-APP2`, `SKU-WH1000XM5`, `SKU-PIXEL9`, `SKU-MXM3S`.

4. Variables utilisées dans tout le guide, **à définir dans le terminal où sont lancés les curl**
   (une variable vide = header non envoyé = `400`) :

   ```bash
   export KEY=$(cat /proc/sys/kernel/random/uuid)   # ou $(uuidgen)
   export ORDER='{"customerId":"CUST-001","items":[{"sku":"SKU-PS5SLIM","quantity":2}]}'
   echo "KEY=[$KEY]"
   ```

5. Raccourcis SQL (mot de passe `1234`) :

   ```bash
   alias psql-orders='PGPASSWORD=1234 psql -h localhost -U postgres -d order_service_db'
   alias psql-stock='PGPASSWORD=1234 psql -h localhost -U postgres -d stock_service_db'
   ```

   Noter le stock de départ, pour le comparer après les scénarios 1 et 2 :

   ```bash
   psql-stock -c "select sku, available_quantity, reserved_quantity from stock_items where sku = 'SKU-PS5SLIM'"
   ```

---

## 1. Création nominale

```bash
curl -i -X POST localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" -H "Idempotency-Key: $KEY" \
  -d "$ORDER"
```

**Attendu**

- `HTTP/1.1 200`, header de réponse `Idempotency-Key: <KEY>`.
- Body : `orderId` renseigné, `status` = `PENDING`, `idempotencyKey` = `<KEY>`.
- Log `orders-service` : `✔ Commande <orderId> créée avec succès`.

Noter l'`orderId` :

```bash
export ORDER_ID=<orderId de la réponse>
```

**Vérifier en base**

```bash
psql-orders -c "select order_id, status, idempotency_key from orders where idempotency_key = '$KEY'"
psql-orders -c "select event_type, aggregate_id, created_at from outbox_event where aggregate_id = '$ORDER_ID'"
```

- 1 ligne dans `orders`. Quelques secondes plus tard, le saga l'a fait passer à `CONFIRMED`.
- 1 ligne `OrderPlacedEvent` dans `outbox_event` (puis `OrderConfirmedEvent` une fois le saga terminé).

---

## 2. Rejeu avec la même clé (cas central)

Simule un client qui n'a pas reçu la réponse (timeout) et renvoie exactement la même requête.

```bash
curl -i -X POST localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" -H "Idempotency-Key: $KEY" \
  -d "$ORDER"
```

**Attendu**

- `HTTP/1.1 200`, **même `orderId`** que le scénario 1.
- `status` = l'état actuel de la commande (`CONFIRMED` si le saga est terminé), pas `PENDING`.
- Log `orders-service` : `♻ Commande déjà existante pour idempotencyKey=<KEY> → renvoi sans création`.
- Aucun appel à catalog-service dans les logs pour cette requête.

**Vérifier en base**

```bash
psql-orders -c "select count(*) from orders where idempotency_key = '$KEY'"                    # → 1
psql-orders -c "select count(*) from outbox_event where aggregate_id = '$ORDER_ID' and event_type = 'OrderPlacedEvent'"   # → 1
psql-stock  -c "select count(*) from stock_reservations where order_id = '$ORDER_ID'"          # → 1 par SKU
psql-stock  -c "select sku, available_quantity, reserved_quantity from stock_items where sku = 'SKU-PS5SLIM'"
```

Le stock n'a bougé que de `quantity` (2), une seule fois, malgré les deux appels.

---

## 3. Header absent

```bash
curl -i -X POST localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -d "$ORDER"
```

**Attendu**

- `HTTP/1.1 400`.
- Aucune nouvelle ligne dans `orders` :

  ```bash
  psql-orders -c "select count(*) from orders"   # inchangé
  ```

---

## 4. Header vide

```bash
curl -i -X POST localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" -H "Idempotency-Key;" \
  -d "$ORDER"
```

> `-H "Idempotency-Key;"` (point-virgule) force curl à envoyer le header avec une valeur vide.
> `-H "Idempotency-Key:"` le supprimerait, ce qui revient au scénario 3.

**Attendu** : `HTTP/1.1 400`, aucune nouvelle commande.

---

## 5. Même body, nouvelle clé

Deux commandes identiques passées volontairement (le client clique deux fois sur « Commander »
en générant une clé à chaque fois) sont deux commandes distinctes.

```bash
curl -i -X POST localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" -H "Idempotency-Key: $(cat /proc/sys/kernel/random/uuid)" \
  -d "$ORDER"
```

**Attendu** : `HTTP/1.1 200`, **nouvel `orderId`**, nouvelle réservation de stock.

> C'est au client de réutiliser la clé pour une même intention d'achat (même formulaire,
> même panier), et d'en générer une nouvelle pour une nouvelle commande.

---

## 6. Même clé, body différent ⚠ limite connue

```bash
curl -i -X POST localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" -H "Idempotency-Key: $KEY" \
  -d '{"customerId":"CUST-001","items":[{"sku":"SKU-MXM3S","quantity":5}]}'
```

**Comportement actuel** : `HTTP/1.1 200` avec la commande du scénario 1 (`SKU-PS5SLIM` × 2).
Le nouveau body est ignoré silencieusement.

**Comportement cible** : `422 Unprocessable Entity`. Il faudra stocker une empreinte (hash) du
body avec la clé et la comparer au rejeu.

---

## 7. Requêtes simultanées avec la même clé

Plusieurs requêtes arrivent en même temps : toutes passent `findByIdempotencyKey` sans rien
trouver, puis la contrainte `uk_orders_idempotency_key` n'en laisse insérer qu'une. Les autres
interceptent la violation après le rollback de leur transaction et renvoient la commande créée
par la première (`OrderServiceImpl.createOrder`).

```bash
export KEY=$(cat /proc/sys/kernel/random/uuid)
for i in 1 2 3 4 5; do
  curl -s -w " → %{http_code}\n" -X POST localhost:8080/api/v1/orders \
    -H "Content-Type: application/json" -H "Idempotency-Key: $KEY" \
    -d "$ORDER" | sed -E 's/.*"orderId":"([^"]+)".*( → [0-9]+)$/\1\2/' &
done; wait
```

**Attendu**

- 5 lignes `<orderId> → 200`, **toutes avec le même `orderId`**.
- Log `orders-service` des requêtes perdantes :
  `♻ Concurrent request already created the order for idempotencyKey=<KEY> → returning it`.
- Une seule commande, un seul évènement :

  ```bash
  psql-orders -c "select count(*) from orders where idempotency_key = '$KEY'"   # → 1
  psql-orders -c "select count(*) from outbox_event o join orders c on o.aggregate_id = c.order_id::text
                  where c.idempotency_key = '$KEY' and o.event_type = 'OrderPlacedEvent'"   # → 1
  ```

> Si le log `♻ Concurrent request…` n'apparaît pas, les appels ne se sont pas chevauchés
> (la 1re requête avait déjà commité) et les suivantes sont passées par le scénario 2 :
> relancer avec une nouvelle clé ou plus d'itérations. Le chevauchement est plus fréquent
> au 1er appel, quand l'appel au catalog-service allonge la transaction.

---

## Avec Postman

La requête **Create Order** de [`postman/saga-pattern.postman_collection.json`](postman/saga-pattern.postman_collection.json)
envoie `Idempotency-Key: {{$guid}}`, soit une nouvelle clé à chaque envoi (scénario 5).
Pour rejouer le scénario 2, remplacer `{{$guid}}` par une valeur fixe et envoyer deux fois.
