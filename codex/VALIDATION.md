# Validation et passage de relais

## Dernière validation complète — 1er novembre 2026

Révision applicative **938a6569b8f2f9d8a27352346dc47c8d7fb7cbf6**,
[run 41253981642](https://github.com/panlelapin/simplercal/actions/runs/41253981642)
terminé avec success via scripts/build-remote. Les changements documentaires
suivants conservent le même code applicatif, les mêmes tests et le même pipeline.

- Contrôle local et functionalCheck sur runner propre réussis : **110 tests JVM
  Release et 19 tests Python**, zéro échec/erreur/ignoré ; Detekt potential-bugs,
  compilation des tests Android et contrats shell/XML/YAML/projet réussis.
  Empreinte locale attestée :
  `cfda5de0f0b1c5c74f791d869e090030eb32a94c51ef84d80589c2ae968a2b0b`.
- Rapports XML Android 14/API 34 et Android 16/API 36 inspectés : **55 tests par
  API**, zéro échec, erreur ou test ignoré, soit **110 exécutions Android réussies**.
  Cela inclut les cinq créneaux, les images des contours/ripple, les gestes dans
  WeekView et le véritable redimensionnement pendant un glissement.
- Release de production signée, réduite par R8 et non débogable construite.
  Variante smoke réduite démarrée à froid sur chaque API : Status: ok,
  LaunchState: COLD, Settings et Today présents dans les deux hiérarchies UI.
- Partition de 2048 Mio utilisée sur les deux émulateurs. Les diagnostics
  confirment la suppression des deux AVD ; nettoyage des images SDK sans erreur.
  Le test Python de lanceur fatal/ADB bloqué réussit aussi localement et en CI.
- APK publié après ces contrôles, récupéré et vérifié par les scripts : package
  com.github.panlelapin.simplercal, minSdk 34, targetSdk 36, debuggable=false,
  signature et SHA-256 vérifiés. Fichier local : build/android-app-arm64-v8a.apk.
  SHA-256 de ce run :
  `f1a4361359b77775a54a48837cb71c4ebe7810b20606c68ded260537b16dd3cf`.
- L'artefact android-test-reports contient les rapports JVM, les résultats séparés
  api-34/api-36, les journaux d'émulateur, smoke-start.log, smoke-ui.xml et cleanup.log.
  GitHub réduit la rétention demandée de quatorze jours à sa limite existante
  d'un jour ; aucune rétention n'a été changée.

Aucun appareil ADB autorisé au préflight : **aucune installation physique**.
Les parcours automatisés utilisent des données contrôlées. Ils ne remplacent ni
les comptes/permissions/Documents/HTTPS réels, ni une vraie mort de processus ou
un transfert de sauvegarde, ni une observation manuelle de tous les rendus.
Aucun chiffre de cadence physique ou trace Perfetto : voir PERFORMANCE.md.

Les sections suivantes sont historiques et décrivent les états successifs avant
cette réussite ; leurs mentions « non validé » ne sont pas l'état actuel.

## Incident de démarrage émulateur — 1er novembre 2026

Le [run 41252284692](https://github.com/panlelapin/simplercal/actions/runs/41252284692),
révision 8fcf1935aa29c8474c363d60d0cd6e53afd3955b, passe functionalCheck et les
constructions Release/smoke. L'émulateur API 34 affiche FATAL avant son démarrage :
6630,55 Mo disponibles, 7372,80 Mo demandés pour userdata. Aucun test Android
n'est lancé. Une commande adb getprop non bornée immobilise ensuite la boucle
d'attente ; la tentative est annulée pour obtenir le journal, sans APK publié.

Le script CI fixe maintenant la partition à 2048 Mio, détecte FATAL/PANIC, borne
chaque sonde ADB et la durée totale du boot, et arrête le groupe de processus de
l'émulateur. Son AVD et son image SDK temporaires sont libérés après chaque API,
en conservant rapports/diagnostics/APK. Les deux étapes Android ont un plafond
de vingt minutes. Aucun adb uninstall ni modification d'un appareil physique.
Un test Python isolé reproduit un lanceur fatal restant vivant et une sonde ADB
bloquée ; il exige un échec borné, sans lancement de Gradle, avec nettoyage.
Le contrôle local de la correction a réussi : 110 tests JVM et 19 Python,
zéro échec/erreur/ignoré ; Detekt, compilation des 55 tests Android et contrats
réussis. Empreinte cfda5de0f0b1c5c74f791d869e090030eb32a94c51ef84d80589c2ae968a2b0b,
révision 938a6569b8f2f9d8a27352346dc47c8d7fb7cbf6. La CI réussie de cette révision,
décrite en tête de ce document, confirme ensuite le boot et les résultats Android.

Les journaux révèlent aussi une limite GitHub existante à **un jour** pour les
artefacts : le workflow demande quatorze jours mais GitHub réduit cette durée.
Aucune rétention ni configuration du dépôt n'a été modifiée.

## Première exécution Android des panneaux et créneaux — 1er novembre 2026

Le [run 41251213290](https://github.com/panlelapin/simplercal/actions/runs/41251213290),
révision 3b4279a5df492f2633884c45d4f7d86551909957, passe la préparation système,
functionalCheck et les constructions Release/smoke. Android 14 exécute les 55 tests :
54 réussis, un échec, zéro ignoré. Android 16 et smoke ne sont pas exécutés ; aucun
APK n'est publié.

Le test de redimensionnement demandait 1000 puis 900 dp à une densité de 0,75 sur
l'écran CI de 640 pixels de haut : les deux demandes étaient contraintes à la même
hauteur réelle. Le geste n'était donc pas annulé et sa validation sélectionnait
normalement le jour suivant. La fixture réduit maintenant la hauteur depuis les
pixels réellement mesurés et exige qu'elle ait changé avant de relâcher le pointeur.
L'assertion de retour au jour initial et celle du maintien hors repli sont conservées.
Le comportement applicatif n'est pas modifié. Le test corrigé réussit ensuite sur
les deux API dans la validation complète décrite en tête de ce document.

## Contrôle local et première tentative distante — 1er novembre 2026

La demande « go tests et compil » délègue le pipeline scripts/build-remote.
Le contrôle local du contenu publié dans d366b87ac2fdbbce0a901b55681236817fecc537
a réussi : **110 tests JVM et 18 tests Python**, zéro échec/erreur/ignoré ;
Detekt potential-bugs, compilation des sources des **55 tests Android** et
contrôles shell/XML/YAML/contrats réussis. Empreinte attestée :
`add19db9856bccdae336dd814bf9b9a151b47ab3718e08fc2c0e5c1ef4f87fd5`.

Le [run 41250203264](https://github.com/panlelapin/simplercal/actions/runs/41250203264)
a été annulé après plus de onze minutes sans progrès dans la préparation APT du
runner, avant functionalCheck distant. Le journal s'arrête sur apt-get update ;
il ne permet pas d'identifier le miroir en cause. Aucun test Android ni APK de
cette tentative. La préparation utilise désormais les seuls dépôts Ubuntu,
avec délais réseau bornés, tentatives limitées, échec explicite et sortie visible ;
les exécutions ultérieures sont décrites plus haut, jusqu'à la réussite complète.

Aucun appareil ADB autorisé au préflight : aucune installation physique.
Les sections « non validé » ci-dessous décrivent les états antérieurs à cette
exécution ; elles ne remplacent pas cette preuve locale datée.

## Créneaux quotidiens — état initial avant validation

La vue calendrier est reliée aux cinq horaires persistés : première ligne depuis
00:01 jusqu'à sa valeur Until, quatre lignes réglées par leur début From, dernière
jusqu'à minuit. L'événement sur plusieurs créneaux est repris partout sauf dans
son dernier créneau, sur sa durée globale. Cinq positions conservées, résumé des
collisions, détail complet pour tous les jours non vides et zone du ViewModel
partagée par la projection et le détail. Voir SCHEDULES.md.

Projection hors thread UI, annulable et protégée par son contexte complet.
Préférences existantes préservées, configuration incomplète/non chronologique
signalée et aucune nouvelle lecture externe de calendrier.

30 tests ajoutés pour cette évolution : 23 JVM et 7 Android/Compose. Totaux définis :
110 JVM, 55 Android et 18 Python. **Aucun contrôle, compilation ou test exécuté pour
ces nouvelles sources.** Aucune CI, aucun commit/push ni APK de cette évolution.
Les résultats ci-dessous décrivent des sources antérieures ; ils ne prouvent pas
la compilation ni l'exécution des créneaux.


## Dernier contrôle local avant les créneaux — panneaux du 31 octobre 2026

Corrections de l’audit AUDIT_PANNEAUX_COMPOSE.md implémentées : géométrie partagée,
insets lus en mesure, hauteur minimale mesurée, scroll ancré pendant le tap,
Surface cliquable et contour unique, conteneurs simplifiés, annulation/reprise
et garde de génération. L’identité de dimanche est aussi conservée après une
sortie puis un retour au groupe weekend pendant le même drag.

30 tests ajoutés : 16 JVM et 14 Android/Compose. Totaux définis : 87 JVM,
48 Android et 18 Python. **scripts/check-local réussi le 31 octobre 2026** :
87 tests JVM Release, zéro échec/erreur/ignoré ; 18 tests Python réussis ;
Detekt potential-bugs, compilation des 48 tests Android et contrôles XML/YAML/contrat
réussis. BUILD SUCCESSFUL en 38 s pour le dernier passage. Empreinte attestée :
`09b923c91799372fef62555ce1accb68ecb1227a2c998b8d7c951625f1f3a793`.
Après ce contrôle des panneaux, des comptes rendus ont été mis à jour, puis
le code des créneaux a été modifié. L’empreinte ci-dessus ne valide donc pas les
sources actuelles ; un nouveau contrôle complet reste nécessaire.

Un passage précédent avait trouvé un ancien argument de couleur dans un test
d’accessibilité après simplification du modèle : corrigé puis contrôle complet
rejoué avec succès, sans assouplissement des vérifications.

Aucun test Android de cette refonte exécuté, aucune nouvelle CI, aucune installation
ou vérification manuelle des panneaux. Aucun profil ni chiffre de cadence physique.
Le dernier APK téléchargé correspond aux anciennes sources.

Ces changements sont dans le worktree basé sur f6c1c77c86321bc64991c48e41403e00e0fc5970,
sans commit/push ni build distant dans cette passe. Les preuves ci-dessous restent
historiques ; [PERFORMANCE.md](PERFORMANCE.md) décrit les mesures encore nécessaires.


## Dernière CI avant la refonte des panneaux — 31 octobre 2026

Révision applicative **ec9adf3806f4b0790b99cb13a9b4f50808f7ae60**, [run 41181942792](https://github.com/panlelapin/simplercal/actions/runs/41181942792)
terminé avec success après correction des prérequis du runner et de referenceZone.
Les changements suivants de documentation ne modifient pas cette révision applicative.

- functionalCheck réussi localement et sur le runner propre : 71 tests JVM Release,
  18 tests Python de scripts, Detekt potential-bugs et compilation des tests Android.
- Rapports Android 14/API 34 et Android 16/API 36 : **34 tests par API**, zéro échec,
  erreur ou test ignoré ; soit 68 exécutions Android réussies. Les deux parcours
  All events conservent leurs assertions de titre complet et passent.
- Release de production signée, réduite par R8 et non débogable construite ; variante
  smoke réduite construite et démarrée à froid sur les deux API. Status: ok et
  présence des commandes Settings/Today vérifiée dans la hiérarchie UI.
- Publication de l'APK après ces contrôles, puis récupération et vérification par
  scripts/build-remote → scripts/make-remote → scripts/verify-apk : package
  com.github.panlelapin.simplercal, minSdk 34, targetSdk 36, debuggable=false.
- APK local : build/android-app-arm64-v8a.apk ; SHA-256 de ce run :
  `07d93391ad84afac93c3791d67b701c0d44ae85e5eea6e5d0ad63801ec2d8640`.
- Rapports et diagnostics conservés dans l'artefact android-test-reports du run,
  avec résultats séparés api-34/api-36, journaux émulateur et smoke-start.log/UI XML.

Aucun appareil ADB autorisé au préflight : **aucune installation physique**.
Ces tests vérifient les parcours automatisés avec données contrôlées, pas les
comptes personnels, le transfert réel d'une sauvegarde, une vraie mort du processus,
les droits du sélecteur Documents, le réseau HTTPS réel ou tous les rendus/insets
sur appareil. Les vérifications manuelles ci-dessous restent nécessaires.
Les preuves plus anciennes restent historiques et ne remplacent pas celles-ci.

## Contrôle local initial — 31 octobre 2026

**scripts/check-local réussi** après corrections, sur la demande « valide ».
71 tests JVM Release et 18 tests Python réussis ; zéro échec, erreur ou test ignoré.
Detekt Release potential-bugs, compilation des sources des 34 tests Android,
syntaxe shell, XML, YAML et contrats projet/snapshot réussis. git diff --check
ne signale pas d'erreur d'espacement.

Validation du worktree modifié, basé sur a9a56659872c700aaa616a3866ce99317d83fc53,
sans nouveau commit/push. Le log ignoré check-local.log contient l'attestation de
contenu courante ; les rapports JVM sont sous
app/build/test-results/testReleaseUnitTest/ et app/build/reports/tests/testReleaseUnitTest/.
Les résultats des suites sont : AppViewModelTest 23, CalendarBoundaryTest 6,
CalendarObservationTest 3, IcalEventsTest 14, IcalRepositoryTest 7,
RegressionTest 10, WeekLogicTest 8.

Corrections nécessaires pendant ce contrôle : quatre empreintes de métadonnées
Maven manquantes, comparées aux octets officiels récupérés via HTTPS ; appel
Composable déplacé hors du constructeur LazyListScope du sélecteur de calendriers ;
arrêt du minuteur du ViewModel dans le finally du corps des tests avant le drainage
de runTest (évite l'avancement infini du temps virtuel et l'épuisement mémoire) ;
type Int de la sélection conservé explicitement dans une fixture SavedStateHandle.
Les lignes de MatrixCursor ont également un type de tableau explicite.

Les 34 tests Android sont **compilés, non exécutés** pour cet état. Aucun APK
assemblé localement, aucun workflow déclenché, aucun APK récupéré/installé.
Le démarrage R8/smoke sur Android 14/16, la CI et les parcours manuels ne sont donc
pas validés. Les sections historiques ci-dessous conservent leur contexte daté ;
elles ne remplacent pas cet état ni ces limites.

## Preuves connues au 30 octobre 2026

Pour le commit applicatif f32842114895e014965fe2f02cef11ec54047197 :

- scripts/check-local réussi ; 18 tests JVM (10 RegressionTest, 8 WeekLogicTest),
  aucun échec/erreur/ignoré, et compilation des tests Compose réussie.
- [Run 41109786714](https://github.com/panlelapin/simplercal/actions/runs/41109786714)
  terminé avec success. Le workflow impose les tests Compose avant publication.
  L'API n'a pas fourni le détail des étapes ni de journal exploitable lors de la
  consultation ; aucun nombre de tests Compose exécutés n'est donc affirmé ici.
- APK récupéré et vérifié, SHA-256
  `809f4ceeae2eadb733d034b53536eebdb9c7a0b26925528589fcf59cf30681e7`.
- Aucun appareil ADB autorisé au préflight : aucune installation, aucun résultat
  de validation visuelle manuelle pour cette livraison.

Ces preuves sont datées et ne valident pas automatiquement des modifications
ultérieures. Le répertoire build/ et check-local.log sont locaux et ignorés ; un
nouveau clone doit refaire les contrôles. Consulter les résultats du run portant
le SHA courant avant de revendiquer un résultat plus récent.

## Carte initiale des tests définis — 31 octobre 2026

L'inventaire détaillé et la portée figurent dans [TESTS.md](TESTS.md) : 71 tests JVM,
34 tests Android/Compose, 18 tests Python, plus un démarrage smoke minifié sur deux
API dans la CI. **Définitions nouvelles et raccordement IMPLEMENTED — NOT VALIDATED.**
Aucune exécution locale/distante n'a été déléguée pour cet ajout. Les nombres datés
plus haut sont les résultats de l'ancien état ; ils ne prouvent pas ces suites.

functionalCheck inclut les tests JVM Release, les tests Python isolés et la
compilation des sources Android. La CI prévoit leur exécution sur Android 14/16,
avec smoke x86_64 et conservation des rapports même en échec. Les dépendances
injectables permettent le temps virtuel et des réponses contrôlées sans comptes
personnels. Le starter du skill ne compte pas dans cet inventaire applicatif.

## Vérifications manuelles restantes

1. Permission initiale, refus, révocation pendant lectures simultanées, retour aux
   réglages et nouvelle autorisation. Calendrier choisi masqué puis supprimé.
2. Création/modification d'événement dans une autre app, rafales provider, panne
   transitoire, fond/reprise, minuit, changement d'heure et de fuseau près de minuit.
   Vérifier absence de données d'une autre semaine/calendrier/fuseau.
3. Rotation et restauration de processus : semaine, dimanche sélectionné, réglages,
   dialogues et scroll. Restauration sur autre installation : calendrier non choisi.
4. Paysage, navigation par boutons/gestes, découpe écran, grande police : aucune
   cible sous les barres système, défilement accessible et horaires utilisables.
5. Plus de neuf événements : bouton tactile, action TalkBack, titres complets et
   horaires dans le détail, absence d'action d'appui long et de cases manuelles.
6. Gestes depuis chaque jour et bandes, animation interrompue, inversion immédiate,
   annulation du pointeur et relâchement ; contrôler aussi contours et jointures
   du weekend, jour courant, ligne de vacances et contraste de chaque thème.

## Avant de transmettre ou réécrire

Lire les instructions, inspecter git status/diff et identifier branche/origin.
Ne pas écraser les changements de l'utilisateur. Pour chaque changement, consigner
le contrat concerné, l'effet observable et la validation effectuée sur le SHA visé.
Distinguer : IMPLEMENTED, contrôles locaux réussis, CI réussie, installation vérifiée,
comportement UI observé. Ajouter les limites plutôt que transformer l'un en l'autre.
Mettre à jour les documents liés à une modification et conserver l'historique utile.

En cas de réimplémentation : reconstruire d'abord modèles/dates/préférences et
repository annulable, puis ViewModel/lifecycle, thèmes, composants/semantics,
géométrie et gestes, enfin pipeline. Chaque étape doit préserver les invariants du
CONTRACT.md. Ne pas inventer de logique d'horaires, jours fériés, région ou backend.


## État après évolution des trois sources — 31 octobre 2026

Les résultats ci-dessus précèdent les nouvelles sources et bornes d'années.
La lecture Android par rôle, les imports iCalendar, leurs caches/timestamps, les
nouveaux réglages et les jours désactivés sont IMPLEMENTED — NOT VALIDATED.
Aucune nouvelle compilation, exécution de tests ou livraison n'a été demandée pour
cette passe. TESTS.md distingue désormais les suites ajoutées et les parcours encore manuels,
notamment la vraie mort du processus, les droits Documents et le transfert Android.
Les limites explicites de parsing sont décrites dans ARCHITECTURE.md.


## Ajout des tests — 31 octobre 2026

92 méthodes de test ajoutées ; total défini : 123, sans compter les deux démarrages
smoke. AppEnvironment/CalendarQuery/CalendarObserverRegistration exposent les
frontières contrôlables ; la factory Android conserve createSavedStateHandle.
L'itérateur DATE de biweekly reçoit une copie privée normalisée UTC, afin de ne
pas dépendre du fuseau global du processus. Les tests correspondants sont définis.

Scripts/CI et variantes modifiés, avec snapshot check-local mis à jour. Aucun
contrôle, test, compilation, commit/push, dispatch, récupération ou installation
exécuté pour cette passe. **IMPLEMENTED — NOT VALIDATED**. Les limites du plan sont
consignées dans TESTS.md ; une relecture des sources ne vaut pas exécution.


## Première tentative CI des trois sources — 31 octobre 2026

Commit e7779a1e68af3a0f7b9ae683831b69cde9d5a96b, run
https://github.com/panlelapin/simplercal/actions/runs/41180654110 : échec dans
pipelineTests, car ripgrep n'était pas installé sur le runner Ubuntu. Les trois
tests qui exécutent check-local dans leur fixture ont détecté le prérequis absent.
Correction : installation explicite du paquet ripgrep dans la préparation CI.
Aucun APK publié pour ce run ; aucun appareil ADB autorisé détecté localement.
La tentative suivante doit valider la correction et exécuter les émulateurs.


## Deuxième tentative CI — 31 octobre 2026

Commit a0e5dc47dd4b1c5b799029667de0600c80878e6e, run
https://github.com/panlelapin/simplercal/actions/runs/41180946984 : functionalCheck,
construction Release signée/réduite et construction smoke réussis. Android 14 :
34 tests exécutés, 32 réussis et 2 échecs dans les parcours All events. Les rapports
JVM confirment les 71 réussites sans échec/ignoré sur le runner propre.

La garde d'affichage de MainScreen comparait loadedEventsZone au fuseau système
global, alors que le ViewModel de ces parcours utilise ZoneOffset.UTC injecté.
Correction : le ViewModel expose referenceZone à l'initialisation et à update ;
la vue valide ses données avec ce même contexte. Les tests de changement de
fuseau vérifient aussi referenceZone. Les assertions des parcours UI sont conservées.
Nouvel essai requis pour les deux API et le démarrage smoke ; aucun APK publié
par ce run, aucune installation physique.
