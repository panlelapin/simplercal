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
