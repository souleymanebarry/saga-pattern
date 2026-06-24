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
