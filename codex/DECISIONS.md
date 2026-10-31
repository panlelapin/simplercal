# Décisions et frontières à préserver

- **Sources de vérité séparées** : contrat produit dans CONTRACT.md, état métier
  dans AppViewModel, progression visuelle dans WeekInteraction. Le callback de
  sélection vers le ViewModel ne doit pas rejouer une animation de tap.
- **Deux marqueurs indépendants** : vacances et férié bancaire par ISO date ; weekend
  automatique. La simulation a ses propres règles reproductibles dans chaque semaine.
  Aucun texte d'événement, nom de calendrier ou locale ne fournit ces marqueurs.
- **Calendrier en lecture seule** : données personnelles du provider Android, pas
  de backend ni copie d'identifiant sur un autre appareil. L'ancien résultat est
  gardé en panne seulement s'il reste valide pour le contexte d'affichage.
- **UI Material** : thème dynamique System par défaut ; les accents génèrent toutes
  les paires HCT. Blanc/noir des jours ordinaires courants/futurs est une exception
  produit explicite, pas un précédent pour ajouter des couleurs brutes ailleurs.
- **Accessibilité avant proportions physiques impossibles** : les proportions restent
  constantes sur une hauteur virtuelle si 6,5 % de l'écran est inférieur à 48 dp.
  Le coût est un défilement ordinaire fréquent sur petits écrans, assumé pour garder
  les cibles tactiles ; ne pas réduire discrètement ce seuil.
- **Un propriétaire des gestes et contours** : enfant pour tap, parent pour drag ;
  nœud sémantique unique avec action de détail. Contour combiné dessiné après les
  enfants ; éviter les doubles traits et les outlines indépendants des sous-boîtes.
- **Animation et drag différents** : 500 ms mesurées par frames pour les taps, pixels
  sans temporisation pour le drag. L'animation Android globale ne pilote pas la durée.
- **Validation sans style imposé** : functionalCheck cherche les bugs et exécute les
  JVM, compile les tests Compose ; CI exécute ces derniers. Les contrôles de style
  restent facultatifs. Ne pas atténuer une règle pour masquer un vrai défaut.
- **APK construit à distance** : wrapper Gradle vérifié et dépendances épinglées,
  workflow manuel, signature stable, aucune désinstallation automatique. Les scripts
  sont le chemin canonique du commit à la vérification finale de l'artefact.
- **Reprise portable** : tout code, documentation et outillage requis est versionné ;
  caches/SDK/JDK/comptes/clés restent externes. Les secrets Actions de l'original
  permettent un nouveau clone sans recopier la clé privée sur le poste.
- **Entrée agent courte** : AGENTS.md route vers codex/ ; le contrat complet déplacé
  depuis l'ancien AGENTS.md est conservé dans CONTRACT.md. Cette réorganisation ne
  modifie pas le comportement de l'app. Les audits PB1/PB2 restent historiques.


## Évolution calendrier convenue

- Trois rôles indépendants : données principales (titres), jours fériés
  (isWEorBankH) et vacances scolaires (isHolidays). Les deux derniers ne rajoutent
  pas de titres dans la liste principale. Le weekend reste reconnu indépendamment.
- Chaque rôle accepte un calendrier Android, un fichier iCalendar local ou une
  URL HTTPS. Pour Android, la synchronisation Internet est la responsabilité d'une
  autre application alimentant le provider système.
- Les trois sources se superposent dans la même vue et sont désactivables
  séparément dans les réglages, en conservant leur configuration.
- iCalendar : lecture lors du choix de la source, puis Refresh manuel par source,
  aucune actualisation au démarrage. Afficher la date de la dernière réussite ;
  conserver les dernières données valides et afficher l'erreur lors d'un échec.
- Prendre uniquement l'année réelle en cours et la suivante. Bloquer la navigation
  hors période ; conserver les sept lignes des semaines limites, dates hors période
  grisées et désactivées.
- Pas d'appui long sur les jours et pas d'édition manuelle de marqueurs. Le bouton
  All events et son action accessible restent le moyen d'ouvrir la liste complète.

Implémentation du 31 octobre 2026 : trois sources, désactivation indépendante,
iCalendar manuel, copies atomiques et dates bornées. Les anciens marqueurs manuels
ne sont plus lus pour l'affichage ; leurs valeurs ne sont pas effacées. La suite
de tests a ensuite été ajoutée et exécutée sur délégation explicite ; les preuves
datées sont dans TESTS.md et VALIDATION.md.
Les limites de l'importeur sont explicites dans CONTRACT.md et ARCHITECTURE.md.

## Créneaux quotidiens confirmés

- Cinq positions fixes dans chaque jour développé, configurées par les cinq
  préférences existantes : première heure = Until depuis 00:01 ; autres = From.
- Les plages intermédiaires finissent au début suivant, la dernière à minuit.
  Les valeurs restent indépendantes et doivent former un ensemble chronologique.
- Un événement sur un seul créneau reste là. Dès qu'il touche plusieurs créneaux,
  afficher tous les créneaux touchés sauf le dernier, sur la durée globale.
  La confirmation sur quatre créneaux est : 1, 2 et 3, sans le 4.
- Les positions vides sont conservées ; plusieurs événements d'un même créneau
  sont résumés par le premier titre et un compteur, sans supprimer les autres.
- L'accès au détail complet est conservé dès qu'un jour a des événements,
  notamment si les horaires sont incomplets ou si un événement ne touche aucune plage.
- Projection asynchrone annulable, sans nouvelle lecture des sources ; données
  métier originales conservées et anciens résultats de projection rejetés.
- Statut : implémenté, contrôle local réussi le 1er novembre 2026 ; compilation
  des tests Android distincte de leur exécution. SCHEDULES.md et VALIDATION.md
  contiennent les règles, preuves datées et limites actuelles.
