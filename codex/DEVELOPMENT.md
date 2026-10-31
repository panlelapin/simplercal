# Développement, clone et livraison

## Prérequis

Le chemin maintenu est un poste Linux avec Bash et outils GNU. Il faut installer
Git, GitHub CLI (`gh`), Python 3 avec PyYAML (`import yaml`), ripgrep (`rg`), coreutils
(notamment sha256sum, readlink avec -z, realpath, sort, tee, cmp, timeout),
setsid (util-linux), find et sed.
JDK 17 et SDK Android sont requis localement pour les contrôles, même si l'APK est
assemblé exclusivement dans GitHub Actions. Android Studio n'est pas obligatoire.

SDK attendu : plateforme android-36, build-tools 36.0.0 et Android command-line tools
avec `cmdline-tools/latest/bin/apkanalyzer`. platform-tools/ADB est optionnel pour
l'installation. Accepter les licences SDK lors de l'installation du SDK. Gradle est
fourni par le wrapper suivi dans Git et télécharge sa distribution vérifiée ; ne
pas imposer un Gradle système. Les dépendances et le premier clone demandent Internet.

Configurer `JAVA_HOME` vers le JDK 17 et `ANDROID_HOME` vers le SDK ;
`ANDROID_SDK_ROOT` est également accepté. Sinon les scripts explorent les dossiers
utilisateur standards décrits dans resolve_toolchains. `local.properties` peut
contenir `sdk.dir=/chemin/du/sdk` et reste ignoré par Git. Les outils de signature
locaux ne sont pas nécessaires pour construire sur le dépôt original déjà configuré.

Authentifier `gh auth login`. Le compte doit pouvoir pousser sur la branche et
déclencher Actions. Configurer aussi l'identité Git si des modifications doivent être
commitées (`git config user.name` / `git config user.email`). Le dépôt original est
`https://github.com/panlelapin/simplercal`, branche `master` ; conserver sa visibilité.
Un lecteur sans ces droits peut cloner/étudier le code, pas publier dans l'original.

## Une seule commande après clonage

```bash
git clone https://github.com/panlelapin/simplercal.git
cd simplercal
./scripts/build-remote
```

L'entrée fonctionne aussi depuis un autre cwd grâce au chemin du script.
`--help` explique ses effets sans lancer de build. Ce wrapper vérifie l'authentification,
appelle `scripts/check-local`, s'arrête sur échec, puis fait exec de
`scripts/make-remote`. Il ne réimplémente aucune opération de ces scripts.

ATTENTION : make-remote stage et commit toutes les modifications non ignorées,
pousse la branche, déclenche le workflow manuel et attend sa conclusion. Relire
ses changements avant d'exécuter. Une branche détachée n'est pas supportée.
La commande ne supprime pas les artefacts distants et ne reconfigure pas la signature.
Les opérations restent réservées à l'utilisateur sauf délégation explicite à l'agent.

## Étapes et résultats

1. check-local : syntaxe shell, découverte JDK/SDK, functionalCheck (Detekt
   potential-bugs avec résolution de types, tests JVM Release et compilation des
   tests Android et suites Python isolées), XML/YAML et contrat projet/snapshots. Son log est check-local.log.
2. Empreinte des chemins, contenu, modes exécutables et cibles symlink ; refus si
   le contenu change pendant le contrôle ou avant/après staging/commit.
3. make-remote : préflight ADB, contrôle de fraîcheur, commit/push, dispatch du
   workflow et suivi d'un nouveau run correspondant au SHA envoyé.
4. CI : JDK/SDK, functionalCheck, clé stable venant des secrets, APK Release réduit
   et non débogable, tests Compose Debug et démarrage smoke réduit sur émulateurs
   Android 14/16 x86_64, vérification APK, SHA-256, publication de l'artefact. Échec des tests = pas de publication.
5. Téléchargement de l'unique APK et checksum, vérification du hash, package, SDK,
   caractère non débogable et signature. Destination : build/android-app-arm64-v8a.apk.
6. Si un appareil autorisé a été choisi au préflight : adb install -r, vérification
   package/version et SHA-256 de l'APK relu depuis le téléphone. Aucun appareil :
   build et récupération possibles, installation ignorée. Avec plusieurs appareils,
   fixer ADB_SERIAL. L'appareil choisi est conservé jusqu'à l'installation.

L'installation ne prouve pas le rendu ni toutes les interactions. L'artefact est
nommé arm64 ; l'ABI filter encadre les bibliothèques natives éventuelles. Il ne
transforme pas à lui seul un APK purement Kotlin en binaire intrinsèquement arm64.
Le footer affiche la ressource OFFICIAL_RELEASE_VERSION ou `---` ; versionName/versionCode
restent définis séparément dans app/build.gradle.kts. Le workflow actuel ne définit
pas OFFICIAL_RELEASE_VERSION.

## Signature et fork

Les quatre secrets Actions nécessaires sont ANDROID_SIGNING_KEYSTORE_BASE64,
ANDROID_SIGNING_STORE_PASSWORD, ANDROID_SIGNING_KEY_ALIAS et ANDROID_SIGNING_KEY_PASSWORD.
Ils sont configurés sur l'original ; un nouveau clone y déclenche le même workflow
sans copie de clé sur le poste. Un fork ne reçoit pas ces secrets.

Pour un fork ou une restauration explicitement autorisée, exécuter manuellement
`scripts/configure-signing`. Il crée/réutilise `signing/` privé et ignoré, vérifie une
sauvegarde hors dépôt puis envoie les secrets après confirmation. Dépendances :
keytool, openssl, base64 GNU et realpath. Sauvegarde par défaut sous
`~/.local/share/simplercal/signing-backups/` ; SIGNING_BACKUP_ROOT peut la déplacer
mais pas dans le dépôt. Une copie sur le même disque n'est pas une sauvegarde hors
appareil. Ne jamais joindre credentials, clé ou valeurs de secrets à un commit/log.
Ne pas remplacer la clé stable de l'original pour faire réussir un build.

En cas de signature incompatible sur le téléphone, le script s'arrête sans
uninstall. Une migration effaçant des données doit être décidée et sauvegardée
explicitement par l'utilisateur.

## Reprise et diagnostic

```bash
./scripts/build-remote --resume NUMERO_DU_RUN_REUSSI
```

Cette variante refait le contrôle local, puis délègue à make-remote --resume. Le
worktree doit être propre et HEAD identique au SHA du run réussi. Elle ne déclenche
pas une compilation nouvelle : elle récupère et vérifie l'artefact existant, tant
qu'il est conservé. Un artefact expiré exige un nouveau run normal. GitHub applique
actuellement une limite d'un jour malgré les quatorze jours demandés dans le
workflow (journaux du 1er novembre 2026) ; vérifier la date d'expiration de l'artefact.
Aucune configuration de rétention n'a été changée. Ne pas présenter un ancien APK
comme celui de nouvelles sources.

En cas d'échec : lire check-local.log ou les logs du run, corriger la cause et
relancer le même point d'entrée. Ne pas baisser les checks, réécrire les checksums
pour masquer un échec ni reproduire manuellement la séquence de livraison.
Les tâches KtLint/Lint/qualityCheck/ShellCheck sont facultatives et ne font pas
partie de la commande normale. Aucun build APK local.

`scripts/cleanup-github-actions.sh` est un outil séparé portant sur les dépôts
publics du compte, pas uniquement SimplerCal. Ne pas l'appeler dans la livraison ;
suppression et changement de rétention ont chacun leur confirmation explicite.


## Suites de tests définies

Voir TESTS.md pour les fichiers et scénarios. functionalCheck inclut pipelineTests,
qui appelle Python unittest dans tests/scripts (Python 3, PyYAML, Bash et Git).
Les doublures gh/adb/keytool/openssl et les dépôts temporaires ne touchent pas aux
services, appareils et clés réels. Aucun SDK n'est invoqué par ces doublures.
Les suites JVM utilisent kotlinx-coroutines-test 1.9.0 et son temps virtuel.

La CI prépare Python/PyYAML, construit aussi assembleSmoke, puis exécute
scripts/ci-device-tests 34 et 36 sur des émulateurs distincts. Ce script est réservé
au runner Actions ; l'utilisateur continue à utiliser les points d'entrée habituels.
Il force la réexécution des tests Android sur chaque API et vérifie que l'interface
smoke réduite affiche Settings et Today. L'artefact android-test-reports conserve
les rapports JVM/Android et build/ci-diagnostics même en échec. Les deux artefacts
demandent 14 jours de rétention ; la limite GitHub existante réduit actuellement
cette durée à un jour. Consulter expires_at plutôt que supposer les quatorze jours
effectifs.

Chaque AVD de test utilise une partition userdata de 2048 Mio et un groupe de
processus dédié. FATAL/PANIC et la mort du lanceur arrêtent le boot ; ses sondes ADB
et son délai global sont bornés. La sortie nettoie uniquement l'AVD et l'image SDK
temporaires de l'API courante sur ce runner jetable, pour garder de la place pour
l'API suivante. Aucun adb uninstall. Les étapes Android ont un plafond de vingt
minutes et conservent les diagnostics même en échec.
Voir les [options officielles de l'émulateur](https://developer.android.com/studio/run/emulator-commandline)
pour -partition-size et -no-metrics.

Validation locale et distante réussie le 31 octobre 2026 : scripts/check-local,
71 tests JVM et 18 tests Python, 34 tests Android par API (34 et 36), constructions
Release/smoke réduites et démarrage smoke sur les deux API.
[Run 41181942792](https://github.com/panlelapin/simplercal/actions/runs/41181942792),
révision applicative ec9adf3806f4b0790b99cb13a9b4f50808f7ae60. APK récupéré et vérifié par
les scripts. Aucun appareil ADB autorisé, donc aucune installation physique.
Ces preuves ne constituent pas une validation visuelle manuelle ; voir VALIDATION.md.
