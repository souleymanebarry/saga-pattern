📦 **Saga Retail Platform**

(Projet Maven Parent : saga-pattern)

🎯 **Objectif du projet**

Ce projet implémente une architecture micro-services orientée événement basée sur le Saga Pattern :
- (orchestré par événements Kafka) pour un cas métier de retail / e-commerce.

Il démontre :

* La cohérence des données sans transaction distribuée

* L’orchestration asynchrone via Kafka

* La séparation claire des responsabilités

* Une architecture prête pour la production

🧱 **Architecture globale**

**Saga-pattern (parent Maven)**

├── catalog-service → Catalogue produits (REST)

├── orders-service → Création & orchestration des commandes

├── stock-service → Vérification & réservation de stock

🧩 **Rôle de chaque micro-service**

| Service             | Responsabilité                                 |
| ------------------- | ---------------------------------------------- |
| **catalog-service** | Source de vérité des produits (SKU, prix, nom) |
| **orders-service**  | Création de commandes + orchestration du Saga  |
| **stock-service**   | Réservation / rejet de stock                   |

🔄 **Parcours utilisateur (Saga):**

1. [x] Le client sélectionne des produits
2. [x] orders-service enrichit la commande depuis catalog-service
3. [x] orders-service publie OrderPlacedEvent
4. [x] stock-service :
   réserve le stock → StockReservedEvent
   ou rejette → StockRejectedEvent

5. orders-service :
   confirme la commande
   ou l’échoue

📖 **Détail complet du flux, des événements Avro et des transitions d'état :** [`docs/saga-flow.md`](docs/saga-flow.md)

📤 **Publication fiable des événements (Transactional Outbox + Debezium CDC, schéma orders ↔ stock) :** [`docs/outbox-cdc.md`](docs/outbox-cdc.md)

🧪 **Tester les Dead Letter Topics avec la console Kafka :** [`docs/dlt-testing.md`](docs/dlt-testing.md)

📡 **Communication inter-services**

| Type          | Utilisation                      |
| ------------- | -------------------------------- |
| **REST**      | catalog-service ← orders-service |
| **Kafka**     | orders ↔ stock                   |
| **DB locale** | Une base par service             |

🧰 **Stack technique**

* Java 17
* Spring Boot 3.x
* Spring Data JPA
* Kafka
* PostgreSQL
* MapStruct
* Lombok
* Maven multi-modules


**🚀 Prochaines étapes possibles**

* Ajout du payment-service
* Compensation Saga (rollback stock)
* Circuit breaker (Resilience4j)
* Cache Redis du catalogue

#   **🚀 Tu es prêt pour la prod**

Si tu veux, je peux maintenant :

🔁 Ajouter DLQ (Dead Letter Topic)

Topics DLT créés : chaque service crée les DLT des topics qu'il consomme.
- orders-service : stock.reserved.dlt et stock.rejected.dlt
- stock-service : order.placed.dlt      
Trois choix de conception :
- Mode d'acquittement : je garde AckMode.MANUAL. MANUAL_IMMEDIATE aurait commité l'offset avant la transaction en base, et un échec de commit aurait    
  fait perdre l'événement. J'ai vérifié dans le code de Spring Kafka 3.3.10 qu'en MANUAL, l'offset du message envoyé en DLT est bien commité.
- Envoi en DLT : la DLT est publiée directement sur Kafka, sans passer par l'outbox. C'est un mécanisme technique, sans donnée métier à garder cohérente
  avec la base.
- Rejeu depuis la DLT : il n'est pas automatique. On corrige la cause, puis on republie le message, par exemple avec Conduktor.

Pour vérifier en conditions réelles : 
redémarrez orders-service et stock-service dans IntelliJ. Je vérifierai ensuite que les 3 topics .dlt existent    
dans Kafka. Je peux aussi envoyer un message illisible sur stock.rejected pour le voir arriver dans stock.rejected.dlt.
┌───────────────────────────┬─────────────┬─────────────────────────────────────────────────────────────────────────────────────────────────────────┐   
│         Décision          │  Verdict    │                                                Pourquoi                                                 │   
│                           │  pour vous  │                                                                                                         │   
├───────────────────────────┼─────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────────────┤   
│ Garder AckMode.MANUAL     │             │ Les listeners d'orders-service sont @Transactional et appellent ack.acknowledge() à l'intérieur de la   │   
│ (pas MANUAL_IMMEDIATE)    │ ✅ À garder │ transaction. MANUAL_IMMEDIATE commiterait l'offset Kafka avant la base : si le commit SQL échoue,       │   
│                           │             │ l'événement est perdu. Avec MANUAL, l'offset part après la transaction.                                 │   
├───────────────────────────┼─────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────────────┤   
│ DLT publiée directement,  │             │ Au moment de l'envoi en DLT, la transaction du listener a déjà été annulée, donc il n'y a aucun état    │   
│ pas via l'outbox          │ ✅ À garder │ métier à garder cohérent. Surtout, l'erreur vient souvent de la base elle-même : si Postgres est        │   
│                           │             │ indisponible, écrire dans l'outbox échouerait aussi et le message ne pourrait jamais sortir.            │   
├───────────────────────────┼─────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────────────┤   
│ Rejeu manuel depuis la    │ ✅ Pour     │ Rejouer automatiquement serait dangereux chez vous, car orders-service n'est pas encore idempotent.     │   
│ DLT                       │ l'instant   │ Rejouer un stock.reserved déjà traité repasserait la commande en CONFIRMED et republierait un           │   
│                           │             │ OrderConfirmedEvent en double. Un rejeu manuel oblige à vérifier l'état de la commande avant.           │   
└───────────────────────────┴─────────────┴─────────────────────────────────────────────────────────────────────────────────────────────────────────┘   
🔐 Ajouter SASL / SSL

🧪 Ajouter tests Kafka embarqués

📊 Ajouter metrics + tracing


docker-compose down -v
docker-compose up -d

GET  http://localhost:8081/subjects                              # liste des sujets                                                                                                                                               
GET  http://localhost:8081/subjects/{subject}/versions           # versions d'un sujet                                                                                                                                            
GET  http://localhost:8081/subjects/{subject}/versions/latest    # dernier schéma                                                                                                                                                 
GET  http://localhost:8081/schemas/ids/{id}                      # schéma par ID global                                                                                                                                           
GET  http://localhost:8081/config                                # niveau de compatibilité global                                                                                                                                 
GET  http://localhost:8081/subjects/{subject}/versions/latest/schema   # juste le schéma brut      
