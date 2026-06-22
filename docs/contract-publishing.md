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

Puis exporter le token avant de déployer :

```bash
export GITHUB_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxx   # PowerShell: $env:GITHUB_TOKEN="ghp_..."
```

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

Dans le `pom.xml` du stock-service, déclarer le repository **et** la dépendance :

```xml
<repositories>
  <repository>
    <id>github-saga</id>
    <url>https://maven.pkg.github.com/souleymanebarry/saga-pattern</url>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>com.barry.saga.retail</groupId>
    <artifactId>retail-event-schema</artifactId>
    <version>1.0-SNAPSHOT</version>
  </dependency>
</dependencies>
```

Le stock-service obtient ainsi **exactement** les mêmes classes générées
(`com.barry.saga.retail.stock.event.StockReservedEvent`, etc.) — impossible de diverger de
namespace ou de champ. Il lui faut le même `<server id="github-saga">` dans son `settings.xml`
(scope `read:packages` suffit).

---

## 4. Publication automatisée (CI)

Le workflow [`.github/workflows/publish-contracts.yml`](../.github/workflows/publish-contracts.yml)
publie l'artefact :
- automatiquement à chaque **release GitHub publiée** ;
- manuellement via **Run workflow** (`workflow_dispatch`).

Il utilise le `GITHUB_TOKEN` intégré (scope `packages: write`) — **aucun PAT à gérer en CI**.

---

## 5. Règle d'or

> Toute évolution d'un `.avsc` = nouvelle version publiée du contrat, **avant** que le
> producteur ou le consommateur ne soit déployé. Les deux services ne se synchronisent
> jamais sur du code local : seulement sur une **version d'artefact publiée**.