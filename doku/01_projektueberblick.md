# SuppCheck – Projektüberblick

## 1. Was ist SuppCheck?

SuppCheck ist eine **Spring Boot Webanwendung** zur Verwaltung und Kostenanalyse von Nahrungsergänzungsmitteln (Supplements). Die Anwendung ermöglicht es, Supplements mit ihren Inhaltsstoffen, Preisen und Herstellern zu pflegen, Preisverläufe zu verfolgen und monatliche Gesamtkosten auszuwerten.

## 2. Technologie-Stack

| Komponente       | Technologie                          |
|------------------|--------------------------------------|
| **Sprache**      | Java 21                              |
| **Framework**    | Spring Boot 4.0.4                    |
| **Build-Tool**   | Maven (mit Maven Wrapper `mvnw`)     |
| **Datenbank**    | MongoDB                              |
| **Template-Engine** | Thymeleaf                         |
| **Diagramme**    | Chart.js 4.4.2 (CDN)                |
| **Boilerplate**  | Lombok                               |
| **Container**    | Docker (Multi-Stage Build)           |
| **Orchestrierung** | Kubernetes via Helm Charts         |
| **CI/CD**        | GitHub Actions                       |
| **Code-Analyse** | GitHub CodeQL                        |
| **Registry**     | GitHub Container Registry (ghcr.io)  |
| **Secrets**      | Azure Key Vault + External Secrets Operator |

## 3. Zentrale Funktionen

- **CRUD für Supplements** – Anlegen, Bearbeiten, Löschen von Supplements mit Inhaltsstoffen
- **Preis-Tracking** – Automatische Preishistorie mit Datum bei jeder Preisänderung (Preis + OVP)
- **Kostenberechnung** – Tages-, Monats- und Workout-Kosten werden live berechnet
- **Kosten-Snapshots** – Berechnete Kosten können gespeichert und als Zeitreihe im Diagramm angezeigt werden
- **Tägliche Einnahme-Übersicht** – Summierung aller Inhaltsstoffe (Trainingstag vs. Ruhetag)
- **Supplement-Vergleich** – Nebeneinander-Vergleich aller Supplements mit ihren Zutaten
- **Filter** – Filterung nach Typ (BASIC, SPORT, EXTENDED, WHEY), Hersteller und Aktivstatus

## 4. Unterstützte Hersteller (Enum `Shop`)

Bodylab24, ESN, VIT4EVER, GEN, BigZone, Gannikus, Sinob, GymNutrition, MoreNutrition, Ruehl24, PremiumBodyNutrition

## 5. Supplement-Typen (Enum `SupplementType`)

| Typ        | Beschreibung                                      | Einnahme-Frequenz   |
|------------|---------------------------------------------------|---------------------|
| `BASIC`    | Basis-Supplements, tägliche Einnahme              | 30 Tage / Monat     |
| `EXTENDED` | Erweiterte Supplements, tägliche Einnahme         | 30 Tage / Monat     |
| `SPORT`    | Sport-Supplements, nur an Trainingstagen           | 15 Tage / Monat     |
| `WHEY`     | Whey-Protein, Sonderberechnung (2,5 Portionen/Tag)| täglich             |

## 6. Funktionsweise Secrets-Management

```
Azure Key Vault
  ├── mongoLogin    (z.B. "suppcheck")
  └── mongoSecret   (z.B. "meinPasswort123")
        │
        ▼
ExternalSecret (mongodb-external-secret)
  → Zieht beide Keys aus dem ClusterSecretStore
  → Baut daraus die URI: mongodb://suppcheck:meinPasswort123@mongodb-service:27017/suppcheck?authSource=admin
  → Erstellt Kubernetes Secret "mongodb-credentials"
        │
        ▼
Deployment (env: SPRING_MONGODB_URI)
  → Liest MONGODB_URI aus Secret "mongodb-credentials"
        │
        ▼
Spring Boot (application-kubitos.properties)
  → spring.mongodb.uri=${SPRING_MONGODB_URI:...}
```
