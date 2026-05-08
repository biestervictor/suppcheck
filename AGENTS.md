# Agent Configuration – SuppCheck

## Development Workflow

### Pflichtschritte bei jeder Änderung

1. **Lokal testen** – Tests müssen lokal grün sein vor dem Push
2. **Push auf `dev`** – löst den GitHub Actions CI/CD-Build automatisch aus
3. **CI/CD abwarten** – Build inkl. Maven-Tests, Docker-Image-Build und Push nach `ghcr.io`
4. **ArgoCD deployed automatisch** – bei erfolgreichem Build zieht ArgoCD das neue Image und deployed auf die Dev-Stage
5. **Dev-Stage testen** – manueller Test unter `https://suppcheck-dev.biester.vip`
6. **Bei Fehler** – Logs/Exception analysieren, Fix implementieren, Schleife ab Schritt 1 wiederholen
7. **Produktion** – nach erfolgreichem Dev-Test: `dev` → `main` mergen. ArgoCD erkennt den Merge auf `main` und aktualisiert das Produktions-Deployment automatisch

### Produktions-URL
- **Prod:** `https://suppcheck-kubitos.biester.vip`
- **Dev:** `https://suppcheck-dev.biester.vip`

### Tests Required
**Every feature change MUST include unit tests.** Before pushing to GitHub:
1. Write tests for new functionality
2. Run tests locally: `mvn test`
3. Tests must pass before push

## External Tools Location

**USB Stick:** `/mnt/usb`

### Java
- **Path:** `/mnt/usb/java21`
- **JAVA_HOME:** `/mnt/usb/java21`

### Maven
- **Maven Wrapper:** `./mvnw` im Projektverzeichnis (bevorzugt)
- **Alternativ:** `/mnt/usb/maven/bin/mvn`
- **Local Repository:** `/mnt/usb/maven-repo`

## Project

### SuppCheck
- **Path:** `/home/victor/suppcheck` (Server) / `/Users/victorbiester/IdeaProjects/suppcheck` (lokal)
- **Type:** Spring Boot Application (Java 21, Spring Boot 4.x)
- **Build:** Maven (mit Maven Wrapper `mvnw`)
- **GitHub Repo:** `biestervictor/suppcheck`
- **Docker Registry:** `ghcr.io/biestervictor/suppcheck`
- **K8s Manifests:** Helm Charts unter `helmcharts/` im Projektverzeichnis
- **K8s Namespace:** `suppcheck`
- **Raspberry Pi:** Cluster läuft auf ARM64

## Prerequisites

**MongoDB required** – läuft unter `192.168.178.141:27017` (konfiguriert in `application.properties`)
- **Datenbank:** `suppcheck`
- **Auth:** via Azure Key Vault Secret (`mongoLogin` + `mongoSecret`)

## Application Startup (lokal)

### Maven Build (ohne Tests)
```bash
./mvnw package -DskipTests
```

### Tests ausführen
```bash
./mvnw test
```

### Run single test class
```bash
./mvnw test -Dtest=DailyIntakeSnapshotServiceTest
```

### Spring Boot starten
```bash
./mvnw spring-boot:run
```
- Läuft auf Port **8080**

## Kubernetes Cluster

### Produktionscluster (MicroK8s auf Raspberry Pi 4)
- **Kubeconfig:** `~/.kube/config`
- **Server:** `https://192.168.178.90:16443`
- **kubectl:** `kubectl` (im PATH via Homebrew) oder `/mnt/usb/kubectl`
- **Architektur:** ARM64 (Raspberry Pi)
- **Namespace:** `suppcheck`

### Helm Chart Struktur
```
helmcharts/
├── Chart.yaml          # appVersion, chartVersion
├── values.yaml         # Prod-Werte (Image, Host, MongoDB)
├── values-dev.yaml     # Dev-Overrides
└── templates/
    ├── deployment.yaml  # 1 Replica, Probes, MongoDB URI aus Secret
    ├── ingress.yaml     # nginx, TLS via my-tls-secret
    ├── service.yaml     # ClusterIP :8080
    ├── namespace.yaml
    ├── cert_es.yaml     # ExternalSecret für TLS-Zertifikat
    ├── mongodb-secret.yaml  # ExternalSecret für MongoDB-Credentials
    └── regcred.yaml     # Image Pull Secret (GHCR)
```

### Deployment prüfen
```bash
kubectl --kubeconfig ~/.kube/config get pods -n suppcheck
kubectl --kubeconfig ~/.kube/config logs -n suppcheck deployment/suppcheck
```

### Azure Key Vault Secret Store (ClusterSecretStore)
- **Name:** `azure-kv`
- **Tenant:** `71b67176-40e1-4d4e-80fe-9251918425b2`
- **Vault URL:** `https://treasurykeyvault.vault.azure.net/`
- **Secrets:**
  - `mongoLogin` → MongoDB Benutzername
  - `mongoSecret` → MongoDB Passwort
  - `imagePullSecret` → GHCR Docker Config als Base64
  - `my-tls-secret` → TLS-Zertifikat

## Referenz-Projekte

### MTG Collection
- **GitHub:** `biestervictor/mtg-collection`
- **Gleiche Cluster-Infrastruktur:** ArgoCD, External Secrets, nginx Ingress
- **CI/CD Muster:** `.github/workflows/` mit Maven Build + Docker Push
