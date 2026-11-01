# Architecture et carte de reconstruction

## Socle et frontières

Un module Gradle `app`, une activité exportée `MainActivity`, aucune activité de
widget, aucun serveur, base de données applicative, compte distant, service de
synchronisation ou écriture dans le calendrier. L’application combine trois sources en lecture seule : principal, fériés et vacances.
Chacune utilise Android (READ_CALENDAR), un document iCalendar ou HTTPS (INTERNET). Les cinq horaires pilotent les cinq lignes de créneaux du calendrier ; ils ne
programment ni alarme ni notification. Kotlin/Compose/Material 3 ; versions exactes
dans les Gradle suivis, minSdk 34, compileSdk/targetSdk 36. Le workflow est manuel.

## Carte du code

Tous les fichiers Kotlin applicatifs ci-dessous sont dans
`app/src/main/java/com/github/panlelapin/simplercal/`.

| Fichier | Responsabilité |
| --- | --- |
| MainActivity.kt | Fenêtre et barres système, thème mémorisé, effets lifecycle/date, remplacement écran semaine/réglages, Scaffold, barre de navigation et erreurs |
| AppViewModel.kt | Source de vérité métier : lundi affiché, sélection, référence temporelle, réglages visibles, préférences, lectures provider et annulation |
| AppEnvironment.kt | Interfaces de stockage/provider/lecture fichier, horloge, fuseau et dispatcher injectables ; adaptateurs Android en production |
| AppModels.kt | Modèles d'état et callbacks entre ViewModel et composables |
| AppConstants.kt | Identifiants de préférences, nombre de jours, poids, dimensions, durée et URL du projet |
| AppPreferences.kt | SharedPreferences, migration du calendrier local, validation des horaires, migration héritée du calendrier local |
| AppBackupAgent.kt | Suppression des identifiants calendrier restaurés, y compris l'ancien emplacement |
| CalendarSources.kt | Rôles/configuration/état par source, préférences locales/portables, snapshots atomiques privés et bornes des années |
| IcalRepository.kt | Lecture explicite document/HTTPS, redirections HTTPS, limites, timeouts et annulation |
| IcalEvents.kt | Parser biweekly, récurrences/fuseaux/exceptions, instances des deux années et cache de calcul par rôle |
| CalendarSourceSettings.kt | Trois sources indépendantes, interrupteurs, choix HTTPS et Refresh/date de réussite |
| CalendarEvents.kt | Repository IO, curseurs et CancellationSignal, résultats succès/erreur, instances stables, répartition par date |
| CalendarChanges.kt | Adaptateur Android et activation des observateurs selon permission, sélection et lifecycle STARTED |
| CalendarObservation.kt | Propriété des inscriptions partielles, annulation et backoff borné, testables avec temps virtuel |
| ThemeModels.kt | Enums persistées, palette d'accents unique et génération du ColorScheme HCT complet |
| WeekLayout.kt | Dates ISO, titre annoté, groupes et poids, assemblage des sept jours et des interactions |
| WeekGeometry.kt | Géométrie en pixels, limites cumulatives arrondies partagées par le rendu et le hit-test, hauteur minimale mesurée et ancrage |
| WeekViewport.kt | Insets lus en mesure, sondes typographiques/Material non placées, viewport standard verticalScroll, mesure des jours et coordination du scroll animé |
| WeekInteraction.kt | État visuel de sélection/animation/drag, progression par frames, stabilisation selon les limites réelles |
| WeekGestures.kt | Arbitrage parent des mouvements ; enfant seul responsable du tap ; dernière position up |
| WeekDragPath.kt | Correspondance distance pixels/focus, première étape depuis les poids interrompus, bornage sans overscroll et seuil Discrete |
| DayRow.kt | Surface Material cliquable et arrondie, deux conteneurs simples, libellé mémorisé, titres/bouton partagés avec les sondes, vacances dessinées et contour unique |
| ScheduleSlots.kt | Validation des cinq heures, lignes fixes et projection temporelle ; règle du dernier créneau exclu, plusieurs jours et transitions de fuseau |
| DayDetails.kt | Liste complète des événements et heures, lecture seule, sans édition de marqueurs |
| SettingsScreen.kt | Permission, calendriers avec compte/propriétaire, horaires Material, choix persistés, lien projet |

Manifest, styles, icônes monochromes, textes anglais et règles de sauvegarde sont
sous `app/src/main/`. Les couleurs de composants viennent du ColorScheme ; seules
les seeds dans ThemeModels et l'exception blanc/noir des jours sont autorisées par
le contrat. Les dessins d'icônes utilisés par Icon sont teintés par Material.

## Flux métier

À la première arrivée, Today choisit le lundi réel courant et aujourd'hui (mercredi
en simulation). Un SavedStateHandle existant restaure la navigation. Les flèches changent le lundi d'exactement une semaine dans les deux années permises,
en conservant l'index sélectionné tant que sa date est autorisée.
Changer le mode de simulation invoque Today. La sélection de dimanche reste distincte
du groupe visuel samedi/dimanche. Les groupes possibles sont 0 à 5.

`update` actualise titre ISO, référence temporelle, permission et bornes des années,
combine les sources déjà valides puis remplace la demande calendrier. Les marqueurs
sont dérivés de la présence d'événements dans les rôles fériés/vacances. Une temporisation de 150 ms conflue les demandes
de semaine ; les trois rôles sont chargés en parallèle et la liste des calendriers
a son propre job. Les imports manuels ont chacun un job indépendant. Le titre est un
AnnotatedString : S, tiret espacé et mois anglais majuscule en 12 sp ; nombres au
style du titre. Le statut passé/courant/futur compare les LocalDate complètes.

Au resume : actualisation, liste des calendriers, minuterie vers le prochain minuit
local. ON_STOP annule les lectures et cette minuterie. Les broadcasts de date,
heure et fuseau provoquent une mise à jour au premier plan. Les observateurs
provider sont indépendants de l'écran visible et actifs seulement au lifecycle
STARTED avec permission et au moins un calendrier Android actif ; ils sont démontés à l'arrêt et
lorsque leurs clés changent. Le retry d'inscription double de 1 à 30 secondes.

Le ViewModel expose aussi referenceZone à la vue, qui le compare à loadedEventsZone
sans relire un autre fuseau global. Le repository capture un fuseau pour la requête de semaine. Les résultats sont
rejetés si la révision, le calendrier, le lundi ou le fuseau ont changé. Les données
affichées doivent aussi porter le bon lundi/calendrier/fuseau et une permission
valide. Une erreur transitoire garde les anciennes données uniquement dans ce
contexte. Un curseur nul est une panne, pas une liste vide. Le calendrier sélectionné
est vérifié sans filtre VISIBLE ; disparition et masquage sont différents.

Les instances portent eventId, instanceId construit avec eventId/début, titre,
début/fin et allDay. Tri début/fin/eventId. Un événement horaire appartient à chaque
jour dont l'intervalle local est recoupé ; un all-day utilise ses dates UTC, fin
exclusive. Aucun nom d'événement n'est interprété comme des vacances ou un férié.

## État et persistance

| Emplacement | Contenu / restauration |
| --- | --- |
| SavedStateHandle | displayed_monday (epoch day), selected_day, settings_visible |
| SharedPreferences `simplercal` | thème, accent, scroll, simulation, Debug1, cinq minutes horaires, type/activation/URL HTTPS par rôle ; anciens marqueurs inutilisés |
| SharedPreferences `calendar_device` | migration selected_calendar_id puis source_ROLE_id/uri ; exclu cloud et transfert, jamais portable entre installations |
| files/ical_sources/ROLE.json | identité de source, texte brut du dernier import réussi et timestamp ; AtomicFile, mutex par rôle, exclu des sauvegardes |
| AppUiState en mémoire | résultats provider, contexte chargé, erreurs et chargements ; relus après recréation |
| WeekInteraction | poids par frame et drag transitoires ; reconstruits au repos depuis la sélection métier |
| rememberSaveable / SaveableStateHolder | position de scroll des écrans, dialogue du jour, sélecteurs et slot horaire ouverts |

La migration reprend l'ancien identifiant depuis `simplercal` sur le même appareil.
L'agent de restauration le supprime des deux emplacements après restauration. Les
minutes acceptées sont 0..1439, -1 représente unset ; les valeurs invalides sont
retirées à la lecture. Les valeurs d'enum inconnues reviennent au défaut documenté.
Voir les clés exactes dans AppConstants et AppPreferences avant toute migration.

## Géométrie, gestes et accessibilité

Au repos, cinq lignes compactes à 6,5 %, deux à 33,75 %. La colonne gauche utilise
la largeur du plus grand libellé complet de la semaine, pas une largeur arbitraire.
Seul le parent de ligne porte l'arrondi et dessine le contour après ses enfants.
La jointure ordinaire appartient à la ligne suivante ; les exceptions du weekend
et du contour courant sont détaillées dans CONTRACT.md.

Scaffold possède les insets horizontaux sûrs. WeekView ajoute les deux bandes
visuelles égales et protège le bas avec le maximum des insets prescrits. La hauteur
virtuelle permet au moins 48 dp par ligne compacte et cinq lignes de créneaux plus le bouton de détail dans les
jours développés. Son minimum est donc environ 738,46 dp même à police normale.
En dessous, verticalScroll remplace la loupe ; un retour d'écran conserve le viewport.

Depuis la reprise des panneaux : WeekViewport utilise SubcomposeLayout pour lire les
insets en phase de mesure et mesurer des sondes non placées : une/cinq lignes, ou
cinq lignes plus le vrai bouton All events, en styles normal et italique. Le minimum
inclut aussi la hauteur réelle du libellé, ses espacements et les traits. Les sondes
n'ont pas d'action accessible et ne sont jamais placées. La largeur du libellé est
mémorisée selon ses textes, le style, la densité, le sens de lecture et le mesureur.
Les paddings du Scaffold sont explicitement consommés ; les formules de protection
conservent les valeurs brutes des insets, sans deuxième padding horizontal.

WeekDayLayout lit les poids dans son MeasurePolicy, pas dans la composition des
textes. WeekRowBounds arrondit les limites cumulées une fois : chaque enfant reçoit
sa hauteur exacte et le détecteur utilise les mêmes limites. La géométrie du viewport
et le lundi sont des clés du détecteur ; leurs changements annulent le geste actif.
Le bas protégé est inerte, tandis que les bandes latérales acceptent Linear.

WeekScrollCoordinator ancre le jour touché à sa position visible, puis adapte cet
ancrage à sa hauteur courante pour le garder entièrement visible quand il tient
dans le viewport. ScrollState et poids sont modifiés dans une même transaction
Snapshot depuis la coroutine d'animation, jamais pendant la mesure. Le scroll brut
est limité à ce suivi ; il s'arrête dès qu'un scroll utilisateur prend la main.
verticalScroll conserve le fling, la sémantique, bring-into-view et la restauration.
L'arrivée initiale rend la sélection visible ; un retour d'écran ne réinitialise
pas le viewport sauvegardé.

Le parent de gestes arbitre en passe Initial avant le clickable enfant. Un mouvement
2D au-delà du touchSlop annule le tap, même sans drag autorisé ; le drag reste
vertical. Discrete attend le milieu du jour développé touché. Linear inverse le
mouvement vertical pour avancer comme une page. Le déplacement porté seulement par
up participe aussi au calcul. La disparition du pointeur ou l'annulation nettoie
le drag. Les bandes latérales sont disponibles en Linear.

Un tap change la sélection et garde l'union des contenus développés pendant les
500 ms calculées par withFrameNanos. Une animation interrompue conserve ses poids
courants. Le drag suit les pixels, sans animation temporelle ; à la fin le groupe
est choisi par la distance quadratique entre limites cumulées réelles et cibles,
avec préférence pour le groupe actuel en cas d'égalité.

L'annulation d'un drag garde ses derniers poids, restaure l'identité de départ et
reprend ensuite une transition de 500 ms vers cette sélection. Aucun timer ne pilote
un drag actif. La boucle temporelle vérifie la génération de demande après chaque
attente de frame : une ancienne coroutine ne peut pas publier au-dessus d'un nouveau
drag avant même l'annulation de LaunchedEffect. La logique suspendue est partagée
avec les tests à BroadcastFrameClock et MotionDurationScale contrôlés.

Le jour utilise Surface(onClick, enabled), qui place l'indication sous son clip.
Son modifier de contour dessine après la surface et son indication. Le contenu est
une Row : un Box alignant le seul libellé et une Column des événements. Le fond
est peint une seule fois par la Surface du jour ; les deux conteneurs carrés sont
transparents et la bande de vacances est dessinée à gauche
de la colonne droite. Aucun contour ou clip indépendant sur ces deux conteneurs.

Chaque jour annonce date anglaise avec année, sélection, expansion et nombre
d'événements. Avec des événements : cinq créneaux et bouton All events, même sous neuf événements.
Le bouton reste tactile, tandis que son action TalkBack est portée par le nœud du
jour. Il n'y a aucune action d'appui long ni case d'édition des marqueurs. Aucun nœud de jour ne doit rester derrière
l'écran de réglages, qui remplace effectivement sa composition.


## Trois sources et iCalendar — implémentation du 31 octobre 2026

Les sources principales fournissent uniquement les titres. Tout événement du rôle
BANK_HOLIDAYS marque ses dates recoupées comme fériées ; SCHOOL_HOLIDAYS les marque
en vacances. Le calcul reste indépendant des titres et des weekends. La simulation
garde ses propres flags. Une source désactivée conserve configuration/cache/date,
mais ne contribue ni titres ni marqueurs. Les anciens ensembles de dates manuelles
ne participent plus à ce calcul.

La lecture Android est annulable via CancellationSignal. Une révocation affecte
les rôles Android, sans supprimer leur configuration ni désactiver les sources
fichier/HTTPS. Un calendrier restauré avec un ID absent est explicitement indisponible.
Les observateurs Events/Calendars dépendent de l'ensemble des IDs Android actifs.

Lors d'un choix fichier ou d'un Refresh : lecture IO limitée à 20 MiB, analyse
complète de la période permise puis remplacement atomique du snapshot. Les erreurs
ne remplacent ni texte ni date de dernière réussite. Les redirections restent HTTPS
(maximum cinq), les timeouts sont 15 s connexion et 30 s lecture. Aucun compte ni
mot de passe dans l'autorité URL ; URL avec token de requête possible, jamais loggée.
Les documents utilisent OpenDocument et un droit de lecture persistant.

Au démarrage, en navigation, après changement de fuseau ou de date : uniquement le
snapshot privé est relu, jamais la source externe. Le cache de calcul conserve une
génération par rôle/texte/année/fuseau pour éviter de réétendre le même fichier à
chaque semaine. Une sélection de document retournée avant ON_RESUME peut différer
son import initial explicite jusqu'à cette reprise. ON_STOP annule les jobs actifs.

biweekly 0.6.8 fournit le parsing et l'itérateur de récurrences. Jackson databind
2.22.4 complète les références optionnelles JSON de la bibliothèque pour le classpath
Release ; l'app n'expose aucun import JSON. Empreintes ajoutées depuis les artefacts
et POM/BOM de Maven Central, sans génération de métadonnées par un build exécuté.
Les dates all-day sont normalisées en UTC, les fins restent exclusives. Les exceptions
RECURRENCE-ID remplacent l'occurrence originale et gardent son identité ; les révisions
sont ordonnées par SEQUENCE puis LAST-MODIFIED/DTSTAMP. Les annulations sont exclues.
DURATION jours/semaines utilise des jours nominaux dans le fuseau de l'événement.

Les limites actuelles échouent explicitement au lieu de publier un résultat partiel :
RANGE sur RECURRENCE-ID, RDATE sous forme de périodes, avertissements de parsing,
plus de 100 000 occurrences retenues, 200 000 itérations par série ou durée > 3660 jours.
Les autres composants iCalendar que VEVENT ne fournissent pas d'événements à la vue.
Sources officielles consultées : [biweekly](https://github.com/mangstadt/biweekly),
[Maven Central](https://repo.maven.apache.org/maven2/net/sf/biweekly/biweekly/0.6.8/).

Les bornes sont calculées sur LocalDate.now().year, indépendamment de la simulation :
1er janvier de cette année au 31 décembre suivant. Les semaines limites restent
entières ; les dates extérieures sont grisées sans titres, marqueurs ou actions.
Le ViewModel et le parent de gestes empêchent leur sélection ; les distances de drag
sont bornées aux groupes valides pour ne pas accumuler d'overscroll aux limites.

Les trois sources sont couvertes par les suites décrites dans TESTS.md ; les
résultats locaux et distants datés figurent dans VALIDATION.md.


## Frontières de test — ajout du 31 octobre 2026

AppViewModel hérite de ViewModel. MainActivity fournit explicitement sa factory
avec createSavedStateHandle ; le constructeur Android assemble AppEnvironment.
La logique ne demande plus d'Application pour lire l'heure ou les dépendances.
En production, Clock.systemUTC est convertie vers le fuseau système à chaque
lecture ; les IO restent sur Dispatchers.IO. Les tests substituent une horloge,
un fuseau et un dispatcher contrôlés, ainsi que les quatre interfaces métier.
CalendarQuery permet de tester les vrais curseurs et CancellationSignal Android
avec des réponses contrôlées, sans lire les comptes du téléphone.

La normalisation privée des DATE iCalendar en dates-heures UTC pour l'itérateur
évite le fuseau global imposé par biweekly à ses itérateurs DATE. Les modèles
retournés restent allDay et gardent leur fin UTC exclusive. La suite JVM de parsing
passe lors des validations locale et CI. La dernière validation complète du
1er novembre 2026 réussit avec 55 tests Android par API et le démarrage smoke sur
Android 14/16 ; les parcours manuels restent distincts, voir VALIDATION.md.

La variante smoke reprend minification/réduction des ressources de Release,
utilise le suffixe .smoke et la clé de test Android explicite pour l'émulateur
x86_64. L'APK Release livré garde son identité et sa signature stable. Le script
ci-device-tests est réservé à GitHub Actions, cible uniquement emulator-5554 et
crée un émulateur jetable par API. Aucun uninstall ni installation physique.

Le contour ordinaire est désormais un chemin ouvert : il omet les seules arêtes
horizontales interdites sans masque rectangulaire coupant les côtés du weekend.
Le contour courant garde un RoundRect fermé, dont les rayons intérieurs sont
réduits d’un demi-trait pour suivre exactement la forme extérieure. Le fond unique
du jour évite aussi une lisière de fond de barre entre le trait et le contenu lors
des arrondis au pixel des paddings. Un jour partiellement rogné en haut conserve
sa position initiale, puis son en-tête est ramené progressivement à zéro avec la
même fraction temporelle que les poids ; il ne saute pas à la première frame.

Une modification de géométrie garde visible une sélection qui l’était auparavant,
notamment lors du passage normal → défilant. Une sélection déjà hors viewport à
cause d’un scroll manuel n’est pas ramenée de force. Cette politique est distincte
de l’arrivée initiale et du retour des réglages, qui conserve le viewport sauvegardé.

## Créneaux reliés au calendrier

SCHEDULES.md détaille les cinq plages et les exemples confirmés. Les anciennes
clés de préférences sont conservées : schedule_time_0 est la fin du premier
créneau commençant à 00:01, les quatre suivantes sont des débuts. Le dernier
créneau finit à minuit. H1 ≤ H2 puis H2 < H3 < H4 < H5 ; un ensemble incomplet
ou non chronologique reste éditable mais ne produit pas d'affectation inventée.

MainScreen transmet scheduleTimes et referenceZone dans WeekViewState. WeekView
capture WeekScheduleInput (lundi, événements déjà filtrés par année, cinq valeurs,
fuseau) et produit WeekScheduleProjection sur Dispatchers.Default avec produceState.
Les boucles du ScheduleProjector vérifient l'annulation ; l'affichage rejette toute
projection dont l'entrée ne correspond plus à l'entrée courante. Le cache des
fenêtres est limité à une projection. Aucun chargement provider/iCalendar n'est
ajouté par cette opération ; les callbacks de réglage restent ceux du ViewModel.

Les plages horaires sont intersectées avec les segments d'offset constant du jour,
les fenêtres contiguës d'un même créneau sont réunies, puis comparées aux instants
originaux des événements. Le premier/dernier créneau global est retrouvé depuis
les dates extrêmes, sans parcourir chaque jour d'un événement long. Si plusieurs
créneaux sont touchés, seul le dernier est retiré ; un événement n'apparaît jamais
deux fois dans une même ligne. Les all-day gardent leurs dates UTC flottantes.

DaySchedule possède cinq ScheduleLine, chacune avec son index, l'heure réglée et
les événements affectés. DayRow affiche le libellé Until/From et le premier titre,
avec (+N) en cas de plusieurs événements ; les créneaux vides gardent leur place.
Le jour compact prend le premier créneau occupé, ou le premier s'il n'y en a pas.
Le bouton et l'action accessible All events sont disponibles pour tout jour ayant
des événements, même en configuration incomplète ou pour un événement hors plage.
Le détail reste fondé sur les instances originales du jour et reçoit referenceZone.

Contrôle local des panneaux et des créneaux réussi le 1er novembre 2026 :
110 tests JVM et 19 Python, Detekt et compilation des 55 tests Android.
Les 55 tests Android réussissent sur chacune des API 34/36, avec démarrage smoke
minifié. L'APK signé est récupéré et vérifié ; aucune installation physique.
Révision, rapports et limites sont consignés dans VALIDATION.md.
