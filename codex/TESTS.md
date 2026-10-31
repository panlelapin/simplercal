# Tests de SimplerCal

Le plan demandé le 30 octobre 2026 est repris et implémenté le 31 octobre 2026.
**Validation locale réussie le 31 octobre 2026**, sur demande « valide » :
scripts/check-local passe avec 71 tests JVM et 18 tests Python, sans échec ni test
ignoré. Les sources des 34 tests Android compilent ; leur exécution sur émulateur,
la construction R8/smoke et les vérifications manuelles restent non effectuées.
Voir VALIDATION.md pour les preuves et corrections. Les résultats antérieurs
concernent un état plus ancien du projet.

## Inventaire défini dans les sources

71 tests JVM, 34 tests Android/Compose et 18 tests Python : 123 méthodes de test
au total, dont 92 ajoutées dans cette passe. Les deux démarrages smoke CI sont
supplémentaires. Ce décompte décrit les définitions ; les résultats réellement obtenus sont
distingués ci-dessus et dans VALIDATION.md.

| Suite | Nombre | Scénarios |
| --- | ---: | --- |
| WeekLogicTest | 8 | Dates, simulation, proportions, identité de dimanche, horaires, titre et multijour ; suite préexistante |
| RegressionTest | 10 | Gestes, curseur nul, all-day UTC, marqueurs, repli accessible ; suite préexistante |
| AppViewModelTest | 23 | Trois rôles, activation indépendante, cache sans import au démarrage/reprise/navigation/recréation, Refresh par rôle, échecs réseau/parsing/écriture, timestamps, changement de source, imports interrompus, permission refusée/rétablie, lectures concurrentes et anciennes réponses, minuit/Nouvel An/DST, fuseau, restauration et simulation |
| IcalEventsTest | 14 | Dates UTC et fin exclusive, RRULE/EXDATE/RDATE, exceptions déplacées/annulées, SEQUENCE/timestamps, titres pliés/échappés, années bornées, ancien flux récurrent, fuseaux flottants et VTIMEZONE, durées nominales/exactes au DST, refus des formats invalides/avancés, invalidation du cache |
| IcalRepositoryTest | 7 | Fermeture de flux, BOM, UTF-8 invalide, document indisponible, limite 20 Mio, redirections HTTPS et refus du downgrade/boucles, erreurs HTTP et annulation |
| CalendarBoundaryTest | 6 | Années autorisées/bissextiles, sept lignes avec jours désactivés, bornage du drag, chevauchements à minuit/DST, identité de source et validation HTTPS |
| CalendarObservationTest | 3 | Inscription partielle, backoff virtuel de 1 à 30 secondes, permission, nettoyage et annulation pendant retry |
| CalendarRepositoryTest (Android) | 6 | Vrais curseurs/signaux Android, réponses contrôlées : curseur nul vs vide, calendrier masqué/supprimé, permission, annulation requête/itération et fermeture, identité/tri/fuseau |
| CalendarPersistenceTest (Android) | 6 | Migration locale, stockage par rôle, séparation portable/device, snapshots persistants/identité/corruption, préférences invalides, nettoyage après restauration |
| CalendarObserverLifecycleTest (Compose) | 1 | START/STOP, permission, sélection, notifications et destruction de composition |
| CalendarFlowTest (Compose) | 5 | Vraie composition applicative : réglages → HTTPS sans permission Android → 12 événements → dernier titre complet ; sources désactivées/réactivées ; semaine absente des réglages ; aucun appui long ; brouillon du dialogue restauré |
| AppRecreationTest (Android/Compose) | 1 | Recréation de MainActivity avec dimanche et réglages conservés |
| DayRowAccessibilityTest (Compose) | 3 | Nœud unique, date complète, détail accessible, jour hors année désactivé sans appui long/détail |
| WeekViewTest (Compose) | 4 | Repli dans les deux modes, viewport conservé, dimanche accessible avec police 200 % et faible hauteur |
| WeekGestureTest (Compose) | 8 | Taps, transition 500 ms, seuils et position finale ; suite préexistante |
| tests/scripts/test_pipeline.py | 15 | Empreintes, suppression avant/après staging, modes/symlinks, fraîcheur avant/après commit, propagation d'échecs et mutation pendant contrôle, flush du log, wrapper, reprise SHA/run/worktree, appareil ADB fixé et absence d'uninstall, sauvegarde de signature hors dépôt et secrets non journalisés |
| tests/scripts/test_project_contracts.py | 3 | Exclusions de sauvegarde, identité des scripts/snapshot, ordre des vérifications CI avant publication et rapports conservés en échec |

Les chemins Kotlin sont sous app/src/test/java/com/github/panlelapin/simplercal/
et app/src/androidTest/java/com/github/panlelapin/simplercal/. Les données de test
sont synthétiques. Les tests de stockage utilisent leurs propres noms de préférences
et répertoires. Les réponses provider/HTTPS sont contrôlées, sans comptes personnels
ni réseau réel. Les scripts sont exercés dans des dépôts temporaires avec doublures
de gh/adb/keytool/openssl ; ils ne livrent ni clé ni APK réels.

## Exécution locale et distante

L'utilisateur lance les scripts suivis selon CONTRACT.md. Aucun de ces ajouts
n'accorde une autorisation permanente à Codex pour exécuter les contrôles.

- scripts/check-local appelle functionalCheck : Detekt potential-bugs, les 71 tests
  JVM Release, les 18 tests Python via pipelineTests et la compilation des sources
  de tests Android. Cela n'exécute pas les 34 tests Android sur le poste.
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
- Le détail complet est vérifié via le bouton tactile et l'action d'accessibilité.
  Un parcours TalkBack réel, les insets/navigation par boutons/gestes et
  l'apparence des contours/couleurs demandent encore une observation manuelle.
- Smoke prouve, lorsqu'il passe, le démarrage minifié de sa variante de test.
  Il ne remplace pas une installation de l'APK stable ni une validation complète
  du comportement de cet APK sur appareil physique.

Mettre à jour les résultats, le SHA et les limites dans VALIDATION.md/PB1.md/PB2.md
à chaque nouvelle exécution autorisée. Ne pas confondre compilation des tests
Android et réussite de leur exécution sur un système Android.
