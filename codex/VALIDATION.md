# Validation et passage de relais

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

## Carte des tests suivis

| Tests | Portée |
| --- | --- |
| WeekLogicTest (JVM) | semaines normales/simulées, dates hors semaine, poids, identité de dimanche, minutes, titre ISO et multijour |
| RegressionTest (JVM) | curseur nul, contenu pendant animation, overscroll, distance depuis origine interrompue, stabilisation, seuil Discrete, marqueurs, all-day/fuseaux, année ISO et hauteur accessible |
| DayRowAccessibilityTest (Compose) | date avec année, sélection/expansion, clic du jour, nœud unique avec dix événements et action de détail |
| WeekGestureTest (Compose) | taps des deux modes, contenu jusqu'à fin de transition, rejet du faux tap Discrete, distance finale et mouvements horizontaux/portés seulement par up |
| WeekViewTest (Compose) | vrai WeekView en repli défilant, sélection dans les deux modes et restauration du viewport après retrait/réapparition de l'écran |

Les tests de gestes montent un conteneur contrôlé ; ceux du WeekView complètent
leur couverture d'intégration. Ils ne prouvent pas les parcours provider, Android
restore, tous les appareils ni l'apparence réelle. Le test de launcher dans le
starter de skill concerne le générateur, pas l'application SimplerCal courante.

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
   horaires dans le détail, marqueurs modifiables uniquement hors simulation.
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
