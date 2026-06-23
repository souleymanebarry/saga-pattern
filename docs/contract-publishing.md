# 📦 Publication du contrat d'événements (`retail-event-schema`)

Le module **`retail-event-schema`** porte les schémas Avro partagés entre `orders-service`
(ce repo) et le **`stock-service`** (repo externe). Pour éviter tout *drift* de schéma, il est
publié comme **artefact Maven versionné sur GitHub Packages** ; les deux services dépendent
alors du **même `.jar`**, garantissant des types et namespaces identiques à la compilation.

> Le Schema Registry reste complémentaire : il garantit la compatibilité **au runtime**
> (mode `BACKWARD`). L'artefact garantit la cohérence **à la compilation**.

- **Coordonnées** : `com.barry.saga.retail:retail-event-schema`
- **Repo Packages** : `https://maven.pkg.github.com/souleymanebarry/saga-pattern`
- **Server id Maven** : `github-saga` (doit matcher entre `distributionManagement`, `settings.xml` et la dépendance côté consommateur)

---

## 1. Authentification (local)

GitHub Packages exige un **Personal Access Token (classic)** avec les scopes :
- `write:packages` (publier)
- `read:packages` (consommer)
- `repo` (si le repo est privé)

Ajouter le serveur dans `~/.m2/settings.xml` (⚠️ ne jamais committer ce fichier) :

```xml
<settings>
  <servers>
    <server>
      <id>github-saga</id>
      <username>souleymanebarry</username>
      <!-- Référence une variable d'env plutôt qu'un token en clair -->
      <password>${env.GITHUB_TOKEN}</password>
    </server>
  </servers>
</settings>
```

Puis exporter le token avant de déployer (⚠️ dans la **même** fenêtre que le `mvn deploy`,
la variable ne vit que le temps de la session) :

```bash
export GITHUB_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxx   # PowerShell: $env:GITHUB_TOKEN="ghp_..."
```

> ⚠️ Le PAT doit être un token **(classic)**. Les *fine-grained tokens* passent mal avec
> Maven / GitHub Packages et provoquent des `401`.

### Vérifier le token avant de déployer

Pour éviter un `401` au moment du `deploy`, on contrôle d'abord l'identité **et** les scopes
du token (PowerShell) :

```powershell
# Identité : doit afficher login = souleymanebarry
(Invoke-WebRequest -UseBasicParsing -Uri https://api.github.com/user `
  -Headers @{ Authorization = "token $env:GITHUB_TOKEN" }).Content | ConvertFrom-Json | Select-Object login

# Scopes : DOIT contenir write:packages pour publier
(Invoke-WebRequest -UseBasicParsing -Uri https://api.github.com `
  -Headers @{ Authorization = "token $env:GITHUB_TOKEN" }).Headers["x-oauth-scopes"]
```

- `login` ≠ celui du `settings.xml` → mauvais token / mauvais compte.
- La ligne des scopes **ne contient pas `write:packages`** → recréer le PAT avec ce scope
  (il coche automatiquement `read:packages`). C'est la cause n°1 des `401`.

---

## 2. Publier (déployer) l'artefact

Depuis la racine du projet, déployer **uniquement** le module de contrats :

```bash
mvn -pl retail-event-schema deploy
```

Seul `retail-event-schema` est publié : `orders-service` / `catalog-service` n'ont pas de
`distributionManagement` et ne sont donc pas des artefacts partagés.

### Versionnage

- La version est héritée du parent (`1.0-SNAPSHOT`). GitHub Packages accepte les `SNAPSHOT`
  (ré-uploadables) — pratique en développement.
- Pour un contrat **stable**, préférer des versions **release immuables** (`1.0.0`, `1.1.0`…) :
  une release ne peut pas être écrasée, ce qui protège les consommateurs. Bumper la version
  à chaque changement de schéma non rétro-compatible.

---

## 3. Consommer l'artefact (côté `stock-service`)

Côté lecture, un PAT avec **`read:packages`** suffit (pas besoin de `write:packages`).

**a) `~/.m2/settings.xml`** de la machine/CI du stock-service — même `id` que le repository :

```xml
<settings>
  <servers>
    <server>
      <id>github-saga</id>
      <username>souleymanebarry</username>
      <password>${env.GITHUB_TOKEN}</password>
    </server>
  </servers>
</settings>
```

**b) `pom.xml`** du stock-service — déclarer le repository **et** la dépendance :

```xml
<repositories>
  <repository>
    <id>github-saga</id>
    <url>https://maven.pkg.github.com/souleymanebarry/saga-pattern</url>
    <snapshots>
      <enabled>true</enabled>           <!-- requis tant qu'on consomme du 1.0-SNAPSHOT -->
      <updatePolicy>always</updatePolicy>
    </snapshots>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>com.barry.saga.retail</groupId>
    <artifactId>retail-event-schema</artifactId>
    <version>1.0-SNAPSHOT</version>
  </dependency>

  <!-- Runtime Avro (sérialisation / désérialisation) -->
  <dependency>
    <groupId>org.apache.avro</groupId>
    <artifactId>avro</artifactId>
    <version>1.11.3</version>
  </dependency>
</dependencies>
```

> ⚠️ L'`id` du `<server>` (`settings.xml`) **doit matcher** l'`id` du `<repository>` (`pom.xml`) :
> c'est la clé qui relie les credentials au repo.

**c) Utilisation** — les classes générées arrivent **compilées dans le jar**, directement importables :

```java
import com.barry.saga.retail.order.event.OrderPlacedEvent;
import com.barry.saga.retail.stock.event.StockReservedEvent;

public void onOrderPlaced(OrderPlacedEvent event) {
    // ... logique de réservation de stock
    StockReservedEvent reserved = StockReservedEvent.newBuilder()
        .setOrderId(event.getOrderId())
        // .setItems(...)
        .build();
    // -> publier sur le topic de réponse
}
```

Le stock-service obtient ainsi **exactement** les mêmes classes générées
(`com.barry.saga.retail.stock.event.StockReservedEvent`, etc.) — impossible de diverger de
namespace ou de champ.

**d) Vérifier la résolution** (avec `$env:GITHUB_TOKEN` positionné) :

```powershell
mvn dependency:get -Dartifact=com.barry.saga.retail:retail-event-schema:1.0-SNAPSHOT
```

---

## 4. Publication automatisée (CI)

Le workflow [`.github/workflows/publish-contracts.yml`](../.github/workflows/publish-contracts.yml)
publie l'artefact :
- automatiquement à chaque **release GitHub publiée** ;
- manuellement via **Run workflow** (`workflow_dispatch`).

Il utilise le `GITHUB_TOKEN` intégré (scope `packages: write`) — **aucun PAT à gérer en CI**.

---

## 5. Dépannage

| Symptôme | Cause probable | Correctif |
|---|---|---|
| `401 Unauthorized` à l'upload | Token sans scope **`write:packages`** | Recréer un PAT **classic** avec `write:packages` ; vérifier via `x-oauth-scopes` (cf. §1) |
| `401` alors que les scopes semblent bons | PAT **fine-grained** au lieu de *classic* | Utiliser un token **(classic)** |
| `401` / variable vide | `mvn deploy` lancé dans une **autre** fenêtre que l'`export`/`$env:` | Exporter le token et déployer dans la **même** session |
| `403 Forbidden` | `url` de `distributionManagement` pointe vers le mauvais repo | Doit cibler `…/souleymanebarry/saga-pattern` |
| `409 Conflict` | Version **release** déjà publiée (immuable) | Bumper la version ; pour le dev, rester en `-SNAPSHOT` (ré-uploadable) |
| Consommateur ne trouve pas le SNAPSHOT | `<snapshots>` non activé côté repository | Activer `<snapshots><enabled>true</enabled></snapshots>` (cf. §3-b) |

> 🔐 Si un token a fuité (collé dans un chat, un log, un commit…), **révoque-le immédiatement**
> dans *Settings → Developer settings → Personal access tokens* et régénère-en un neuf.

---

## 6. Règle d'or

> Toute évolution d'un `.avsc` = nouvelle version publiée du contrat, **avant** que le
> producteur ou le consommateur ne soit déployé. Les deux services ne se synchronisent
> jamais sur du code local : seulement sur une **version d'artefact publiée**.