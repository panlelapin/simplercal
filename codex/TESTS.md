# Tests de SimplerCal

La suite contient **110 tests JVM, 55 tests Android/Compose et 19 tests Python**,
soit 184 méthodes définies. L'évolution des créneaux ajoute 30 tests : 22 de
projection, un de préférences/ViewModel, six d'interface et un parcours de réglages.

**Contrôle local réussi le 1er novembre 2026** : 110 tests JVM et 19 tests Python,
zéro échec/erreur/ignoré ; Detekt potential-bugs, compilation des 55 tests Android
et contrats shell/XML/YAML/projet réussis via scripts/build-remote.
Ces résultats couvrent les panneaux, les créneaux et l'échec borné du démarrage
émulateur malgré une commande ADB bloquée. La CI réussit également :
**55 tests sur API 34 et 55 sur API 36**, zéro échec/erreur/ignoré, plus les deux
démarrages smoke minifiés. Révision applicative
938a6569b8f2f9d8a27352346dc47c8d7fb7cbf6,
[run 41253981642](https://github.com/panlelapin/simplercal/actions/runs/41253981642).
APK signé récupéré et vérifié ; aucune installation physique. Voir VALIDATION.md
pour les preuves détaillées et les limites.

## Inventaire défini dans les sources

110 tests JVM, 55 tests Android/Compose et 19 tests Python : 184 méthodes définies.
Les deux démarrages smoke CI sont supplémentaires. Définitions, compilation des
sources Android, exécution sur Android et mesure physique sont des preuves distinctes.

| Suite | Nombre | Scénarios |
| --- | ---: | --- |
| WeekLogicTest | 8 | Dates, simulation, proportions, identité de dimanche, horaires, titre et multijour ; suite préexistante |
| WeekGeometryTest | 12 | Frontières pixel par pixel, tailles minimales mesurées, bandes latérales/basse, vendredi ancré sans saut, chemins arrondis, annulation continue, scroll manuel prioritaire, fin de semaine, taps rapides et retour au dimanche pendant un drag, jour partiellement rogné sans saut initial |
| ScheduleSlotsTest | 22 | Cinq bornes, première fin et autres débuts, ordre/incomplétude, 1/2/3/4/5 créneaux, fins exclusives, 00:01/minuit/pause, collisions, changement d’horaires, plusieurs jours, UTC all-day, points/erreurs, fuseaux/DST, événements longs, snapshot immuable et annulation |
| ScheduleSlotsUiTest (Compose) | 6 | Cinq positions vides/occupées, Until/From, événement sur deux/quatre créneaux, changement d’horaires sans ancienne affectation, détail conservé en configuration incomplète, plusieurs événements dans une ligne |
| WeekAnimationTest | 4 | Horloge de frames contrôlée, durée 500 ms aux échelles 0/0,5/1/10, frames retardées, ancien timer sans annulation incapable d’écraser un drag et reprise continue après annulation |
| RegressionTest | 10 | Gestes, curseur nul, all-day UTC, marqueurs, repli accessible ; suite préexistante |
| AppViewModelTest | 24 | Trois rôles, activation indépendante, cache sans import au démarrage/reprise/navigation/recréation, Refresh par rôle, échecs réseau/parsing/écriture, timestamps, changement de source, imports interrompus, permission refusée/rétablie, lectures concurrentes et anciennes réponses, minuit/Nouvel An/DST, fuseau, restauration et simulation |
| IcalEventsTest | 14 | Dates UTC et fin exclusive, RRULE/EXDATE/RDATE, exceptions déplacées/annulées, SEQUENCE/timestamps, titres pliés/échappés, années bornées, ancien flux récurrent, fuseaux flottants et VTIMEZONE, durées nominales/exactes au DST, refus des formats invalides/avancés, invalidation du cache |
| IcalRepositoryTest | 7 | Fermeture de flux, BOM, UTF-8 invalide, document indisponible, limite 20 Mio, redirections HTTPS et refus du downgrade/boucles, erreurs HTTP et annulation |
| CalendarBoundaryTest | 6 | Années autorisées/bissextiles, sept lignes avec jours désactivés, bornage du drag, chevauchements à minuit/DST, identité de source et validation HTTPS |
| CalendarObservationTest | 3 | Inscription partielle, backoff virtuel de 1 à 30 secondes, permission, nettoyage et annulation pendant retry |
| CalendarRepositoryTest (Android) | 6 | Vrais curseurs/signaux Android, réponses contrôlées : curseur nul vs vide, calendrier masqué/supprimé, permission, annulation requête/itération et fermeture, identité/tri/fuseau |
| CalendarPersistenceTest (Android) | 6 | Migration locale, stockage par rôle, séparation portable/device, snapshots persistants/identité/corruption, préférences invalides, nettoyage après restauration |
| CalendarObserverLifecycleTest (Compose) | 1 | START/STOP, permission, sélection, notifications et destruction de composition |
| CalendarFlowTest (Compose) | 6 | Vraie composition applicative : réglages → HTTPS sans permission Android → 12 événements → dernier titre complet ; sources désactivées/réactivées ; semaine absente des réglages ; aucun appui long ; brouillon du dialogue restauré |
| AppRecreationTest (Android/Compose) | 1 | Recréation de MainActivity avec dimanche et réglages conservés |
| DayRowAccessibilityTest (Compose) | 3 | Nœud unique, date complète, détail accessible, jour hors année désactivé sans appui long/détail |
| WeekViewTest (Compose) | 4 | Repli dans les deux modes, viewport conservé, dimanche accessible avec police 200 % et faible hauteur |
| WeekPanelIntegrationTest (Compose) | 11 | Vraie WeekView avec hauteur suffisante et insets non nuls, deux bandes latérales, seuil Discrete mesuré, bas inerte, resize/changement de semaine pendant drag, vendredi visible frame par frame en repli, cinq créneaux et détail à police 200 %, dimanche et limites d’année |
| DayPanelRenderingTest (Compose) | 3 | Image pendant une pression : ripple découpé et contour préservé ; changement de poids mesurant les jours sans recomposer leur contenu ; continuité des côtés samedi/dimanche sans trait horizontal |
| WeekGestureTest (Compose) | 8 | Taps, transition 500 ms, seuils et position finale ; suite préexistante |
| tests/scripts/test_pipeline.py | 15 | Empreintes, suppression avant/après staging, modes/symlinks, fraîcheur avant/après commit, propagation d'échecs et mutation pendant contrôle, flush du log, wrapper, reprise SHA/run/worktree, appareil ADB fixé et absence d'uninstall, sauvegarde de signature hors dépôt et secrets non journalisés |
| tests/scripts/test_project_contracts.py | 3 | Exclusions de sauvegarde, identité des scripts/snapshot, ordre des vérifications CI avant publication et rapports conservés en échec |
| tests/scripts/test_ci_device_tests.py | 1 | Émulateur fatal restant vivant et ADB bloqué : échec borné, aucun lancement des tests Android, ressources temporaires libérées |

Les chemins Kotlin sont sous app/src/test/java/com/github/panlelapin/simplercal/
et app/src/androidTest/java/com/github/panlelapin/simplercal/. Les données de test
sont synthétiques. Les tests de stockage utilisent leurs propres noms de préférences
et répertoires. Les réponses provider/HTTPS sont contrôlées, sans comptes personnels
ni réseau réel. Les scripts sont exercés dans des dépôts temporaires avec doublures
de gh/adb/keytool/openssl ; ils ne livrent ni clé ni APK réels.

## Exécution locale et distante

L'utilisateur lance les scripts suivis selon CONTRACT.md. Aucun de ces ajouts
n'accorde une autorisation permanente à Codex pour exécuter les contrôles.

- scripts/check-local appelle functionalCheck : Detekt potential-bugs, les 110 tests
  JVM Release, les 19 tests Python via pipelineTests et la compilation des sources
  de tests Android. Cela n'exécute pas les 55 tests Android sur le poste.
- Le workflow manuel rejoue functionalCheck sur le commit envoyé, construit Release
  et smoke, puis lance les tests Android sur les émulateurs Android 14/API 34 et
  Android 16/API 36 x86_64. Les tests sont forcés à se réexécuter pour chaque API.
- Chaque émulateur installe aussi smoke et vérifie son démarrage et la présence
  réelle de Settings/Today dans la hiérarchie UI. Smoke conserve R8, la réduction
  des ressources et le caractère non débogable ; son suffixe .smoke et sa clé de
  test explicite le distinguent de l'APK de production signé avec la clé stable.
- Une erreur de vérification empêche la publication de l'APK Release. Le workflow
  téléverse android-test-reports avec always(), y compris après échec : rapports
  JVM/Android, journaux émulateur et diagnostics smoke. Aucun matériau de signature.

Clock/fuseau/dispatcher et repositories sont injectables via AppEnvironment.
Les tests du ViewModel et du retry utilisent le temps virtuel ; aucun vrai minuit
ni délai machine n'est nécessaire. Les tests de transport/provider utilisent des
latches bornés uniquement pour rendre observable l'annulation d'un appel bloquant.

## Portée et vérifications encore manuelles

Le plan initial a guidé les suites ci-dessus ; les distinctions suivantes restent
nécessaires pour éviter de revendiquer une couverture inexistante :

- La recréation d'activité, une nouvelle instance avec SavedStateHandle et la
  restauration de composition sont couvertes séparément. Elles ne constituent
  pas une vraie mort du processus Android puis relance par le système.
- Les callbacks de permission et erreurs provider sont contrôlés. Le dialogue
  système réel de permission, les particularités OEM, les comptes/synchronisations
  d'une autre application et les droits persistants du sélecteur Documents
  nécessitent encore un parcours sur appareil.
- Le nettoyage de restauration et les exclusions XML sont testés ; un vrai backup
  Android et son transfert vers une autre installation restent à vérifier.
- Les tests HTTPS utilisent des connexions simulées. Les certificats, un serveur
  HTTPS réel, ses timeouts et les coupures réseau du système ne sont pas exercés.
- En CI, le détail complet est vérifié via le bouton tactile et l'action d'accessibilité.
  Un parcours TalkBack réel, les insets/navigation par boutons/gestes et
  l'apparence des contours/couleurs demandent encore une observation manuelle.
- Smoke prouve, lorsqu'il passe, le démarrage minifié de sa variante de test.
  Il ne remplace pas une installation de l'APK stable ni une validation complète
  du comportement de cet APK sur appareil physique.

Mettre à jour les résultats, le SHA et les limites dans VALIDATION.md/PB1.md/PB2.md
à chaque nouvelle exécution autorisée. Ne pas confondre compilation des tests
Android et réussite de leur exécution sur un système Android.

## Performance des panneaux

Les tests à BroadcastFrameClock prouvent la durée et la protection des générations,
avec MotionDurationScale injecté, sans modifier les réglages Android. Le test de
phase mesure une modification réelle des limites et compte les compositions du
contenu. Le test d’image attend que l’indication soit effectivement visible avant
de comparer les coins et le contour ; l'horloge native Android peut différer de
l'horloge Compose. Le résultat de leur exécution CI figure dans VALIDATION.md.

Le protocole d’appareil et de traces est dans [PERFORMANCE.md](PERFORMANCE.md).
Aucun chiffre de cadence n’est déduit des tests fonctionnels ni d’un émulateur.

## Évolution des créneaux

ScheduleSlotsTest contient les exemples confirmés, les limites temporelles et les
changements d'heure. AppViewModelTest ajoute la mise à jour/restauration des cinq
valeurs sans import de fichier. CalendarFlowTest vérifie les libellés accessibles
Until/From et l'ouverture du vrai sélecteur existant. Les anciens tests de neuf
titres visibles sont adaptés à cinq événements sur cinq créneaux ; le détail de
cent événements conserve son assertion sur le dernier titre complet.

Les tests Compose attendent une projection précise avec waitUntil, puisque le
calcul s'effectue en arrière-plan, sans délai arbitraire. Le parcours de réglages
ne change pas le sélecteur Material ni la persistance. Les 22 tests de projection
et le test de préférences/ViewModel passent localement. Voir SCHEDULES.md pour la
spécification complète et VALIDATION.md pour l'exécution Android.
