# PB2 — Suivi des trente corrections

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
- Ces tests ne couvrent pas toutes les combinaisons de comportements et n'ont pas encore été exécutés. Ne pas annoncer « tout passe » à partir de ce document.

## Étapes manuelles

1. Signature configurée : `scripts/configure-signing` a créé la clé et envoyé les quatre secrets à GitHub sur demande explicite. Il a vérifié une copie privée de la clé et des credentials avant l'envoi, dans `/home/senee/.local/share/simplercal/signing-backups/signing-20260930T112453Z-kycTfi3H/`. Les dossiers ont le mode 700 et les fichiers 600 ; `signing/` est ignoré par Git. Cette copie sur le même ordinateur ne protège pas d'une perte de disque : une copie hors appareil reste recommandée. Ne jamais mettre la clé ou les credentials dans Git ni dans ce document.
2. Exécuter `scripts/check-local`, puis `scripts/make-remote`. La CI lancera également les tests sur émulateur avant de publier l'artefact.
3. Si l'installation actuelle est signée avec l'ancienne clé debug, une migration initiale est nécessaire ; le script refuse de désinstaller/effacer les données à votre place.

Les scripts réutilisables de `scripts/` et `skill/make_android_app/scripts/` ont reçu les mêmes changements. La skill installée hors du dépôt n'a pas été modifiée.
