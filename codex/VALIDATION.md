# Validation et passage de relais

## État courant — validation locale du 31 octobre 2026

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

## Carte des tests définis — 31 octobre 2026

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
