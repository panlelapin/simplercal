# PB2 — Suivi des trente corrections

> Panneaux et cinq créneaux : implémentés. Contrôle local réussi le 1er novembre
> 2026 : 110 tests JVM et 19 Python, zéro échec/erreur/ignoré ; Detekt, compilation
> des 55 tests Android et contrats du projet réussis.
> Les 55 tests Android réussissent sur chaque API 34/36, ainsi que les deux
> démarrages smoke minifiés. APK signé récupéré/vérifié ; aucune installation
> physique ni mesure de cadence. Voir
> [SCHEDULES.md](codex/SCHEDULES.md), [l’audit ciblé](codex/AUDIT_PANNEAUX_COMPOSE.md)
> et [VALIDATION.md](codex/VALIDATION.md) pour les résultats Android, CI, APK
> et les limites physiques. Les entrées ci-dessous sont des états historiques.

Statut commun : **IMPLEMENTED — NOT VALIDATED**. Le code, les scripts et les tests ont été modifiés ; Codex n'a lancé ni compilation, ni tests, ni check-local, ni make-remote. Aucun point ci-dessous n'est présenté comme vérifié sur appareil. La configuration de signature a ensuite été explicitement déléguée à Codex et effectuée le 30 septembre 2026 ; voir ci-dessous.

1. Un curseur nul du provider devient une erreur explicite, pas une semaine vide réussie ; les dernières données et la sélection sont conservées (`CalendarEvents.kt`).
2. Le retour de sélection du ViewModel ne relance plus la sélection UI ; une sélection répétée conserve le contenu développé jusqu'à la fin de l'animation (`WeekInteraction.kt`).
3. Toutes les assertions du contrat de projet renvoient explicitement un échec, même sous le `if` de Bash (`scripts/check-local` et snapshot).
4. L'installation utilise `adb install -r` et ne désinstalle plus automatiquement l'application, préservant ses données en cas de signature incompatible (`scripts/make-remote` et snapshot).
5. Les releases exigent une clé stable fournie par secrets GitHub ; un assistant manuel crée/réutilise la clé privée ignorée et demande confirmation avant l'envoi des secrets (`scripts/configure-signing`, Gradle, workflow).
6. `functionalCheck` inclut les tests JVM ; la CI exécute aussi les tests Compose sur émulateur avant publication de l'APK (Gradle, workflow).
7. L'existence du calendrier est contrôlée sans filtre VISIBLE ; les modifications des calendriers et des événements sont observées, et une suppression produit une erreur sans effacer la sélection (`CalendarEvents.kt`, ViewModel, observer).
8. Une permission refusée/révoquée met à jour l'état réel de l'UI et annule les lectures (`AppViewModel.kt`).
9. Les échecs transitoires d'inscription de l'observateur sont visibles et réessayés avec temporisation bornée (`CalendarChanges.kt`).
10. Les requêtes remplacées sont annulées via `CancellationSignal`, la file séquentielle obsolète est supprimée et les réponses sont contrôlées par révision/semaine/calendrier/fuseau (repository, ViewModel).
11. L'identifiant numérique du calendrier est isolé dans des préférences exclues des sauvegardes/transferts ; l'agent de restauration supprime aussi l'ancien identifiant provenant d'une sauvegarde antérieure (`AppPreferences.kt`, `AppBackupAgent.kt`, manifest, XML).
12. L'observateur et le travail calendrier/minuit cessent en arrière-plan et reprennent avec actualisation au retour (cycle de vie, ViewModel).
13. Discrete attend le franchissement du milieu du jour touché avant de démarrer ; Linear démarre après touchSlop et conserve le sens d'un défilement de page (`WeekGestures.kt`, `WeekDragPath.kt`).
14. La distance accumulée est bornée et l'overscroll rejeté, permettant une inversion immédiate du mouvement (`WeekDragPath.kt`).
15. Un drag interrompant une animation part des poids réellement affichés et utilise la distance de leurs limites, pas celle d'un groupe idéal (`WeekDragPath.kt`, `WeekInteraction.kt`).
16. Un déplacement dépassant touchSlop annule le tap enfant même sur un petit jour où Discrete interdit le drag (`WeekGestures.kt`).
17. Le dernier déplacement transmis avec le relâchement est pris en compte avant la stabilisation (`WeekGestures.kt`).
18. Scaffold est l'unique propriétaire des insets sûrs horizontaux ; les bandes gauche/droite ajoutent uniquement la marge visuelle (`WeekInteraction.kt`, écran principal).
19. Une hauteur virtuelle suffisante et un défilement ordinaire remplacent la loupe lorsque l'écran ou la taille de police rend les proportions fixes impraticables (`WeekLayout.kt`).
20. Les réglages remplacent la composition principale au lieu de la superposer à des jours encore interactifs/accessibles (`MainActivity.kt`).
21. Un bouton All events et un appui long ouvrent une liste défilable contenant tous les événements, leurs titres complets et horaires (`DayDetails.kt`, `DayRow.kt`).
22. Les vacances/jours fériés normaux disposent de marqueurs persistés par date, éditables dans ce détail ; la simulation garde ses propres valeurs et ignore ces marqueurs (préférences, ViewModel, détail).
23. Le lien tente directement ACTION_VIEW et signale l'absence de navigateur, sans faux négatif dû à la visibilité des packages (`SettingsScreen.kt`).
24. Les schémas dynamiques et HCT sont mémorisés par configuration/mode/seed, et ne sont plus recalculés à chaque recomposition (`MainActivity.kt`).
25. Les contours sont dessinés une seule fois, après les enfants, avec réserve de place interne et un propriétaire par jointure ; les anciens états de bordure des sous-conteneurs sont supprimés (`DayRow.kt`, modèles).
26. L'italique emploie FontStyle.Italic/FontSynthesis.Style sans inclinaison géométrique supplémentaire (`DayRow.kt`).
27. `make-remote --resume` refuse un worktree source modifié, évitant de présenter un ancien APK comme celui du code courant (script et snapshot).
28. L'empreinte ignore les chemins réellement supprimés avant et après staging ; elle reste indépendante de l'index et du commit, vérifie les modifications pendant le check, et le log est vidé avant sortie (scripts et snapshot).
29. Le nettoyage exige une confirmation séparée pour la rétention, y compris sans artefacts ; les erreurs d'inventaire/suppression/rétention produisent un code d'échec (`cleanup-github-actions.sh`).
30. AGENTS.md décrit un contrat unique sans anciennes instructions incompatibles ; PB1 distingue désormais implémentation et validation, et le snapshot de skill décrit les tests/signatures sans prétendre modifier la skill globale.

## Tests ajoutés et vérification restante

Première exécution locale demandée le 30 septembre 2026 : les 17 tests JVM passent.
Deux défauts de configuration ont été identifiés et corrigés avant le build distant :
activation explicite des tests Release sous AGP 9, et remplacement de la constante
BuildConfig de version par une ressource Android générée, résolue par Detekt sans
modifier son classpath de dépendances. Les résultats du contrôle final et
de la compilation sont communiqués séparément ; cette note ne préjuge pas du résultat CI.

- JVM : curseur nul, conservation du contenu pendant animation, overscroll/inversion, origine visuelle interrompue, seuil Discrete, marqueurs réels/simulation, événements all-day/fuseaux, limite d'année ISO, hauteur accessible.
- Compose : vrais taps en Discrete/Linear, contenu compacté après 500ms, rejet du faux tap pendant drag Discrete interdit, déplacement final au relâchement. Le test de sémantique déjà présent reste inclus en CI.
- Restent à vérifier sur appareil : changements provider/permission, calendrier masqué/supprimé, cycle arrière-plan/minuit/fuseau, restauration Android, contours et insets en paysage/navigation à boutons, grande police, liste de plus de neuf événements, fluidité des deux gestes.
- Les tests Compose listés ici ne disposent pas de résultat consigné dans ce document.
  Le résultat historique des 17 tests JVM ci-dessus ne valide pas les modifications ultérieures. Ne pas annoncer « tout passe » à partir de ce document.

## Étapes manuelles

1. Signature configurée : `scripts/configure-signing` a créé la clé et envoyé les quatre secrets à GitHub sur demande explicite. Il a vérifié une copie privée de la clé et des credentials avant l'envoi, dans `/home/senee/.local/share/simplercal/signing-backups/signing-20260930T112453Z-kycTfi3H/`. Les dossiers ont le mode 700 et les fichiers 600 ; `signing/` est ignoré par Git. Cette copie sur le même ordinateur ne protège pas d'une perte de disque : une copie hors appareil reste recommandée. Ne jamais mettre la clé ou les credentials dans Git ni dans ce document.
2. Exécuter `scripts/check-local`, puis `scripts/make-remote`. La CI lancera également les tests sur émulateur avant de publier l'artefact.
3. Si l'installation actuelle est signée avec l'ancienne clé debug, une migration initiale est nécessaire ; le script refuse de désinstaller/effacer les données à votre place.

Les scripts réutilisables de `scripts/` et `skill/make_android_app/scripts/` ont reçu les mêmes changements. La skill installée hors du dépôt n'a pas été modifiée.


## Corrections après audit du 30 octobre 2026

Statut : **IMPLEMENTED — NOT VALIDATED**. Aucune compilation, aucun test ni script
check-local/make-remote n'a été exécuté par Codex pour cette passe.

- Gestes : annulation des taps sur distance 2D, arbitrage parent en passe Initial
  avant les enfants, prise en compte du mouvement porté seulement par up, et
  annulation du drag si le pointeur actif disparaît.
- Drag interrompu : stabilisation par distance entre les limites réellement
  affichées et celles des groupes au repos, avec préférence pour le groupe actuel
  en cas d'égalité. Les contenus encore agrandis restent présents pendant le drag.
- Calendrier : mémorisation du fuseau des résultats ; exclusion des données d'un
  ancien fuseau ou sans permission ; annulation commune des deux lectures lors
  d'une révocation. Le calendrier sélectionné et ses préférences sont conservés.
- Réglages : erreur du calendrier sélectionné visible ; saisie Material numérique
  dans les fenêtres basses, contenu du sélecteur horaire défilable.
- Accessibilité : date avec année ; bouton All events toujours tactile, action
  accessible portée par le jour unique plutôt qu'un second nœud cliquable.
- Défilement accessible : position conservée au retour de l'écran ; une nouvelle
  sélection peut la repositionner. Le seuil minimal de 48 / 0,065 = 738,46 dp de
  hauteur utile est intentionnel : le réduire violerait les cibles de 48 dp.
- Barres système : fond surfaceContainer et apparence des icônes liés au thème
  choisi, notamment sur Android 14. Leur visibilité reste inchangée.
- Signature : sauvegarde canonique hors dépôt, contrôle des ancêtres symboliques,
  et refus de matériel de signature suivi par Git ou non ignoré.
- Livraison : appareil fixé au préflight ; empreinte des cibles symboliques sans
  perte des sauts de ligne terminaux ; contrôle de fraîcheur répété après staging
  et commit, avec refus d'un worktree modifié.
- Snapshot : scripts réutilisables synchronisés, assistant de signature inclus,
  starter à signature stable et workflow avec tests Compose, métadonnées de
  dépendances synchronisées, test de lancement
  du starter fourni. La skill globale n'est pas modifiée.
- MATERIAL.md est aligné sur AGENTS.md. Les cinq horaires restent uniquement
  des valeurs enregistrées ; ils ne programment aucune alarme ou notification.

Tests de régression ajoutés ou étendus (définis, non exécutés) : relâchement après
interruption d'animation dans les deux directions ; annulation horizontale ;
mouvement transmis uniquement par up ; nœud accessible unique avec dix événements ;
WeekView réel dans la hauteur de repli et restauration de sa position.

Vérifications restant nécessaires : scripts/check-local puis scripts/make-remote
par l'utilisateur ; gestes sur appareil, changement de fuseau avec échec provider,
révocation pendant deux lectures, restauration Android, sélecteur horaire en
paysage/grande police, contours (dont weekend courant), insets navigation à boutons
et contraste des barres sous Android 14 et versions ultérieures. Ni cet audit,
ni la présence de ces tests ne constituent une preuve de fonctionnement à l'écran.


## Exécution déléguée le 30 octobre 2026

À la demande explicite « lance tout ça », Codex a exécuté scripts/check-local.
Après correction des appels d'API dans les nouveaux tests Compose, le contrôle
complet a réussi : 18 tests JVM, aucun échec ni test ignoré ; Detekt Release,
compilation des tests Compose, parsing shell/XML/YAML et contrat des scripts réussis.
La compilation des tests Compose ne prouve pas leur exécution ; celle-ci appartient
à la CI Android 14. La livraison distante et les vérifications manuelles visuelles
restent distinctes de ce résultat local. Les statuts non validés ci-dessus décrivent
l'état avant cette exécution, et ne doivent pas être interprétés comme des résultats CI.


## Suppression de l'appui long — demande utilisateur ultérieure

**IMPLEMENTED — NOT VALIDATED** : les jours utilisent un simple clickable Material,
sans action d'appui long ni annonce d'accessibilité associée. Le détail All events
est en lecture seule ; les cases Vacation/Bank holiday et leurs callbacks/setters
sont supprimés. Les anciennes préférences ne sont pas effacées. Les mentions d'appui
long et d'édition manuelle plus haut dans ce rapport décrivent l'ancien comportement.
Aucun contrôle, test, build, commit ou push exécuté pour cette modification.
Les nouvelles règles des trois sources calendrier sont consignées dans
codex/DECISIONS.md comme restant à implémenter.


## Trois calendriers et période bornée — 31 octobre 2026

**IMPLEMENTED — NOT VALIDATED**. Chaque rôle principal/fériés/vacances possède sa
source Android, document iCalendar ou HTTPS et son activation indépendante. Seul
le principal fournit les titres. Les autres marquent les dates recoupées ; aucune
édition manuelle et aucun appui long. Les anciennes valeurs ne sont pas effacées,
mais ne servent plus à l'affichage.

iCalendar : import initial explicite puis Refresh manuel, jamais au démarrage ;
snapshot atomique privé, droit document persistant et date de dernière réussite,
conservation de la dernière copie en échec. IDs Android/documents non portables,
snapshots exclus des sauvegardes. Parsing/récurrences par biweekly, limites et
formats avancés explicitement refusés dans codex/ARCHITECTURE.md.

La navigation et la sélection sont bornées à l'année réelle courante et suivante,
y compris les gestes. Dates hors période grisées et désactivées dans les semaines
limites, recalcul au changement d'année. Les lectures et imports cessent à ON_STOP.

Aucune compilation, aucun test, aucun contrôle local, commit/push ou build distant
lancé pour cette évolution. Les dépendances et empreintes ont été renseignées par
lecture des artefacts officiels Maven Central. Les contrôles habituels et scénarios
réels (imports, récurrences, annulations, permission, cache, frontière des années)
restent à exécuter. À cette étape, l'ajout de tests était différé ; sa reprise est décrite ci-dessous.


Ajout des tests du 31 octobre 2026 — IMPLEMENTED — NOT VALIDATED : 92 méthodes
supplémentaires, soit 71 JVM, 34 Android/Compose et 18 Python définies au total.
Temps/repositories injectables, provider et transport contrôlés, suites de scripts
isolées, CI Android 14/16 et démarrage smoke R8 avant publication. Le calcul des
récurrences DATE utilise une copie UTC indépendante du fuseau du processus.
Aucun test, contrôle, build, commit/push ou déploiement exécuté pour cette passe.
Voir codex/TESTS.md et codex/VALIDATION.md pour la portée et les limites manuelles.


Validation locale du 31 octobre 2026, déléguée par « valide » : scripts/check-local
réussi, 71 tests JVM et 18 tests Python réussis, zéro échec/erreur/ignoré. Detekt,
compilation des sources des 34 tests Android, shell/XML/YAML et contrats réussis.
Corrections du sélecteur Compose, du nettoyage des minuteurs de test, d'une fixture
SavedStateHandle et de quatre empreintes Maven vérifiées. Détails dans
codex/VALIDATION.md. Worktree non commité ; aucun push, workflow, APK ou installation.
Les tests Android restent compilés mais non exécutés, et le rendu sur appareil,
le démarrage réduit par R8 ainsi que les parcours système restent non validés.


Première tentative distante du 31 octobre 2026 : run 41180654110 échoué à cause
de ripgrep absent du runner pour les tests de scripts. Installation explicite
ajoutée au workflow ; nouvel essai requis. Aucun APK publié par ce run, aucun
appareil ADB autorisé. Les résultats locaux précédents restent distincts.


Deuxième tentative distante du 31 octobre 2026 : run 41180946984. Release signée
et smoke construits, contrôles fonctionnels réussis. Android 14 : 32/34 tests
réussis ; les deux parcours All events détectent un mélange entre fuseau injecté
du ViewModel et fuseau global relu par la vue. referenceZone est désormais transmis
par l'état du ViewModel et utilisé dans la garde d'affichage. Assertions conservées,
validation des deux API et smoke à refaire. Aucun APK publié par ce run.


Validation distante réussie le 31 octobre 2026 — révision applicative ec9adf3806f4b0790b99cb13a9b4f50808f7ae60,
run https://github.com/panlelapin/simplercal/actions/runs/41181942792 :
71 tests JVM et 18 tests Python réussis ; 34/34 tests Android sur API 34 et 34/34
sur API 36, sans échec/erreur/ignoré ; démarrage à froid smoke R8 et présence
Settings/Today vérifiés sur les deux API. Release stable signée et non débogable
construite, publiée, récupérée et vérifiée par la chaîne suivie.
SHA-256 : 07d93391ad84afac93c3791d67b701c0d44ae85e5eea6e5d0ad63801ec2d8640.
Les deux échecs précédents et leurs corrections sont décrits dans codex/VALIDATION.md.
Aucun appareil ADB autorisé : aucune installation physique. Les parcours système
réels et vérifications visuelles manuelles restent non validés.
