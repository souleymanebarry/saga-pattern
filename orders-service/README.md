# 🧩 Orders Service

Service de gestion des commandes dans une architecture 
**microservices event-driven**, basé sur **Kafka** et une **Saga distribuée**.

---

## 📌 Description

`orders-service` est le **point d’entrée principal du parcours utilisateur**.  
Il orchestre la création des commandes, garantit l’intégrité
métier et coordonne les services externes via des événements Kafka.

---

## 🚀 Démarrage de l’environnement (Windows)

```bash
# Démarrer Zookeeper
./bin/windows/zookeeper-server-start.bat ./config/zookeeper.properties

# Démarrer Kafka
./bin/windows/kafka-server-start.bat ./config/server.properties

# Consommer les événements de commande
./bin/windows/kafka-console-consumer.bat --topic order.placed --bootstrap-server localhost:9092 --from-beginning

🧠 Modèle de données – Origine des champs
| Champ         | Fournisseur     | Raison / Responsabilité                 |
| ------------- | --------------- | --------------------------------------- |
| `customerId`  | Client          | Identité de l’acheteur                  |
| `sku`         | Client          | Produit sélectionné depuis le catalogue |
| `productName` | catalog-service | Source de vérité produit                |
| `unitPrice`   | catalog-service | Sécurité / prévention de fraude         |
| `quantity`    | Client          | Intention d’achat                       |
| `totalAmount` | orders-service  | Calcul métier côté serveur              |

👉 Règle clé:
- Le SKU vient du client

- Le nom et le prix viennent exclusivement du catalog-service

- Le total est toujours calculé côté serveur.

🏗️ Responsabilités des composants
| Élément         | Rôle                                   |
| --------------- | -------------------------------------- |
| Controller      | Exposition API, gestion des DTO        |
| Mapper          | Conversion DTO ↔ Entity (MapStruct)    |
| Service         | Logique métier & orchestration Saga    |
| Repository      | Accès base de données                  |
| CatalogClient   | Intégration REST externe               |
| Kafka           | Diffusion et consommation d’événements |
| catalog-service | Source de vérité produit               |

----------------------------------------------------------

🔄 Flux métier global (Saga distribuée) 

    orders-service
    |
    |  OrderPlacedEvent (order.placed)
    v
    stock-service
    |
    |-- Vérifie stock pour chaque SKU
    |-- Si OK → réserve
    |-- Sinon → refuse
    |
    +--> StockReservedEvent (stock.reserved)
    +--> StockRejectedEvent (stock.rejected)

---------------------------------------------------------------
📦 orders-service
🧭 Rôle du service

orders-service est le point d’entrée principal du parcours utilisateur.
Il permet de :

Créer une commande à partir des produits sélectionnés

Valider les règles métier (quantité, idempotence, cohérence)

Enrichir les lignes de commande depuis catalog-service

Démarrer un Saga distribué via Kafka

Réagir aux événements de stock (stock.reserved, stock.rejected)

🏗️ Architecture interne
| Couche     | Responsabilité                           |
| ---------- | ---------------------------------------- |
| Controller | Manipule uniquement des DTOs             |
| Mapper     | Conversion DTO ↔ Entity                  |
| Service    | Logique métier et orchestration du Saga  |
| Repository | Persistance en base de données           |
| Kafka      | Publication et consommation d’événements |

🔄 Flux de création de commande
Le client appelle POST /api/v1/orders

orders-service :

Vérifie l’idempotence

Enrichit les items via REST (catalog-service)

Calcule le total

Persiste la commande

Publie OrderPlacedEvent sur Kafka (order.placed)

Attend les réponses du stock-service

Met à jour le statut de la commande selon l’événement reçu

📡 API REST
➕ Créer une commande

POST /api/v1/orders

{
  "customerId": "CUST-22335",
  "items": [
    { "sku": "SKU-IP15P", "quantity": 2 },
    { "sku": "SKU-PS5-SLIM", "quantity": 1 }
  ]
}

➡️ Le prix, le nom du produit et le total ne viennent jamais du client.

🔍 Consulter une commande

GET /api/v1/orders/{orderId}

📤 Événements Kafka émis
| Event               | Topic           | Description              |
| ------------------- | --------------- | ------------------------ |
| OrderPlacedEvent    | order.placed    | Démarrage du Saga        |
| OrderConfirmedEvent | order.confirmed | Saga terminé avec succès |
| OrderFailedEvent    | order.failed    | Saga échoué              |

📥 Événements Kafka consommés

| Event              | Topic          | Action réalisée             |
| ------------------ | -------------- | --------------------------- |
| StockReservedEvent | stock.reserved | Confirmation de la commande |
| StockRejectedEvent | stock.rejected | Annulation de la commande   |

🧩 Dépendances externes

- catalog-service (REST)

- stock-service (Kafka)

- PostgreSQL

- Kafka

⚙ Scalabilité & amélioration de robustesse (optionnelle)
- Enrichissement catalogue en une seule requête

- GET /products?skus=SKU1,SKU2,SKU3:
✔️ Évite les appels REST multiples (N+1)
✔️ Meilleure performance et meilleure résilience

 🔐 Points KEYS:
*  Idempotence supportée

* Total calculé côté serveur

* Source de vérité produit = catalog-service

* Architecture event-driven

* Résilience via Saga distribuée

* Source de vérité produit = catalog-service

* Résilience via Saga

| Champ         | Qui le fournit  | Pourquoi                  |
| ------------- | --------------- | ------------------------- |
| `customerId`  | Client          | Identité de l’acheteur    |
| `sku`         | Client          | Référence produit choisie |
| `productName` | catalog-service | Source officielle         |
| `unitPrice`   | catalog-service | Sécurité / anti-fraude    |
| `quantity`    | Client          | Intention d’achat         |
| `totalAmount` | order-service   | Calcul métier             |

| Élément         | Rôle                     |
| --------------- | ------------------------ |
| Controller      | DTO + Mapper             |
| Service         | Entités + Repositories   |
| CatalogClient   | Intégration externe      |
| Catalog-service | Source de vérité produit |
| Kafka           | Diffusion d’état         |
