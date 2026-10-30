# Architecture et carte de reconstruction

## Socle et frontières

Un module Gradle `app`, une activité exportée `MainActivity`, aucune activité de
widget, aucun serveur, base de données applicative, compte distant, service de
synchronisation ou écriture dans le calendrier. L'application lit un calendrier
Android avec READ_CALENDAR. Les cinq horaires sont des préférences ; ils ne
programment ni alarme ni notification. Kotlin/Compose/Material 3 ; versions exactes
dans les Gradle suivis, minSdk 34, compileSdk/targetSdk 36. Le workflow est manuel.

## Carte du code

Tous les fichiers Kotlin applicatifs ci-dessous sont dans
`app/src/main/java/com/github/panlelapin/simplercal/`.

| Fichier | Responsabilité |
| --- | --- |
| MainActivity.kt | Fenêtre et barres système, thème mémorisé, effets lifecycle/date, remplacement écran semaine/réglages, Scaffold, barre de navigation et erreurs |
| AppViewModel.kt | Source de vérité métier : lundi affiché, sélection, référence temporelle, réglages visibles, préférences, lectures provider et annulation |
| AppModels.kt | Modèles d'état et callbacks entre ViewModel et composables |
| AppConstants.kt | Identifiants de préférences, nombre de jours, poids, dimensions, durée et URL du projet |
| AppPreferences.kt | SharedPreferences, migration du calendrier local, validation des horaires, marqueurs par date |
| AppBackupAgent.kt | Suppression des identifiants calendrier restaurés, y compris l'ancien emplacement |
| CalendarEvents.kt | Repository IO, curseurs et CancellationSignal, résultats succès/erreur, instances stables, répartition par date |
| CalendarChanges.kt | Observateurs Events/Calendars, permission et lifecycle STARTED, réinscription après erreur avec backoff |
| ThemeModels.kt | Enums persistées, palette d'accents unique et génération du ColorScheme HCT complet |
| WeekLayout.kt | Dates ISO, titre annoté, groupes et poids, géométrie semaine, insets et repli défilant |
| WeekInteraction.kt | État visuel de sélection/animation/drag, progression par frames, stabilisation selon les limites réelles |
| WeekGestures.kt | Arbitrage parent des mouvements ; enfant seul responsable du tap ; dernière position up |
| WeekDragPath.kt | Correspondance distance pixels/focus, première étape depuis les poids interrompus, bornage sans overscroll et seuil Discrete |
| DayRow.kt | Libellé mesuré, fonds des deux conteneurs, titres, vacances, contours à propriétaire unique et sémantique du jour |
| DayDetails.kt | Liste complète des événements et heures, édition immédiate des marqueurs sauf en simulation |
| SettingsScreen.kt | Permission, calendriers avec compte/propriétaire, horaires Material, choix persistés, lien projet |

Manifest, styles, icônes monochromes, textes anglais et règles de sauvegarde sont
sous `app/src/main/`. Les couleurs de composants viennent du ColorScheme ; seules
les seeds dans ThemeModels et l'exception blanc/noir des jours sont autorisées par
le contrat. Les dessins d'icônes utilisés par Icon sont teintés par Material.

## Flux métier

À la première arrivée, Today choisit le lundi réel courant et aujourd'hui (mercredi
en simulation). Un SavedStateHandle existant restaure la navigation. Les flèches
changent le lundi d'exactement une semaine en conservant l'index sélectionné.
Changer le mode de simulation invoque Today. La sélection de dimanche reste distincte
du groupe visuel samedi/dimanche. Les groupes possibles sont 0 à 5.

`update` actualise titre ISO, référence temporelle, permission et marqueurs, puis
remplace la demande calendrier. Une temporisation de 150 ms conflue les demandes
de semaine ; la liste des calendriers a son propre job. Le titre est un
AnnotatedString : S, tiret espacé et mois anglais majuscule en 12 sp ; nombres au
style du titre. Le statut passé/courant/futur compare les LocalDate complètes.

Au resume : actualisation, liste des calendriers, minuterie vers le prochain minuit
local. ON_STOP annule les lectures et cette minuterie. Les broadcasts de date,
heure et fuseau provoquent une mise à jour au premier plan. Les observateurs
provider sont indépendants de l'écran visible et actifs seulement au lifecycle
STARTED avec permission et calendrier choisis ; ils sont démontés à l'arrêt et
lorsque leurs clés changent. Le retry d'inscription double de 1 à 30 secondes.

Le repository capture un fuseau pour la requête de semaine. Les résultats sont
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
| SharedPreferences `simplercal` | thème, accent, scroll, simulation, Debug1, cinq minutes horaires et ensembles ISO des marqueurs |
| SharedPreferences `calendar_device` | selected_calendar_id ; exclu cloud et transfert, jamais portable entre installations |
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
virtuelle permet au moins 48 dp par ligne compacte et neuf lignes de texte dans les
jours développés. Son minimum est donc environ 738,46 dp même à police normale.
En dessous, verticalScroll remplace la loupe ; un retour d'écran conserve le viewport.

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

Chaque jour annonce date anglaise avec année, sélection, expansion et nombre
d'événements. Avec plus de neuf événements : huit titres et bouton All events.
Le bouton reste tactile, tandis que son action TalkBack est portée par le nœud du
jour. L'appui long ouvre aussi le détail. Aucun nœud de jour ne doit rester derrière
l'écran de réglages, qui remplace effectivement sa composition.
