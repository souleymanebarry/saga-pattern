# **🧠 Principes d’architecture respectés**
* Event-driven
* No shared database
* Idempotence
* Isolation métier
* Saga choreography
* Clean Architecture

# **🚀 Lancer le service**
mvn spring-boot:run

# **🧪 Tests recommandés**
* Consommation Kafka
* Réservation concurrente
* Cas stock insuffisant
* Redelivery Kafka

# **🔮 Évolutions possibles**

* Compensation de stock (rollback)
* Timeout de réservation
* DLQ Kafka
* Monitoring (Prometheus)
* Exposition REST (admin stock)

# **✅ Résumé**

stock-service :
* est autonome
* est fiable
* est critique dans le Saga
* ne dépend d’aucun autre service

# **👉 Un vrai participant Saga, propre et scalable.**

**Prochaine étape logique** :

* 🔁 Compensation stock
* 💳 payment-service
* 📊 diagramme Saga final
* 🧪 tests d’intégration Kafka
