# SimplerCal

Application Android Kotlin / Jetpack Compose, interface anglaise, une semaine à
sept jours dont deux agrandis. Trois sources : événements principaux, jours fériés et vacances scolaires,
chacune sélectionnable dans Android, un fichier iCalendar local ou une URL HTTPS
et désactivable séparément. Refresh iCalendar manuel avec date de dernière réussite,
thèmes Material You et gestes Discrete/Linear. Vue limitée à l'année réelle en cours
et à la suivante, sans appui long.

L'évolution trois sources est implémentée mais pas encore compilée/testée ; les
résultats de validation antérieurs ne valident pas ces modifications.
Android 14 minimum ; identifiant `com.github.panlelapin.simplercal`.

## Cloner et construire à distance

Sur une machine Linux équipée des [prérequis](codex/DEVELOPMENT.md#prérequis) et
avec GitHub CLI authentifié sur un compte ayant accès en écriture au dépôt :

```bash
git clone https://github.com/panlelapin/simplercal.git
cd simplercal
./scripts/build-remote
```

Cette commande exécute le contrôle local, puis délègue à `make-remote` le
commit/push des modifications éventuelles, le build GitHub Actions, les tests
Compose sur émulateur et le téléchargement de l'APK vérifié dans
`build/android-app-arm64-v8a.apk`. Aucun APK n'est assemblé localement. JDK et SDK
locaux restent nécessaires pour les contrôles et la vérification du résultat.
Si un appareil ADB autorisé est disponible au préflight, le script le met à jour
avec `adb install -r` ; sinon la livraison continue sans installation.

Les sources, le wrapper Gradle et ses sommes de contrôle, les métadonnées de
vérification des dépendances, les tests, les scripts et le workflow sont suivis
par Git. Les secrets de signature restent dans GitHub Actions ; ils ne doivent
jamais être ajoutés au dépôt. Un clone de ce dépôt utilise ses secrets existants.
Un fork doit configurer sa propre signature stable avant sa première release.

## Comprendre, modifier ou réécrire

Lire [codex/README.md](codex/README.md) : contrat produit intégral, architecture,
persistance, cycles de vie, gestes, outils, livraison, décisions et validation.
[AGENTS.md](AGENTS.md) est le point d'entrée automatiquement découvert par les agents.
Les rapports [PB1](PB1.md) et [PB2](PB2.md) conservent l'historique des audits.
