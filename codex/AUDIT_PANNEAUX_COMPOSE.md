# Audit des panneaux Compose — 31 octobre 2026

> Évolution ultérieure : les cinq horaires pilotent désormais cinq lignes fixes
> de créneaux. Voir [SCHEDULES.md](SCHEDULES.md). Le contrôle local commun aux
> panneaux et créneaux a réussi le 1er novembre 2026 : 110 tests JVM, 18 Python,
> Detekt, compilation des 55 tests Android et contrats du projet. L'exécution
> Android, la CI et l'APK sont consignés dans [VALIDATION.md](VALIDATION.md).

> Les constats ci-dessous décrivent la révision auditée. Une reprise applicative
> a ensuite été implémentée ; son statut et ses preuves sont consignés dans
> [VALIDATION.md](VALIDATION.md). Les sections d'audit suivantes restent historiques.

Révision inspectée : `f6c1c77c86321bc64991c48e41403e00e0fc5970`.
Audit statique du code et des tests, confronté aux sources officielles AndroidX.
Aucun code applicatif modifié ; aucun contrôle, test, build ou parcours sur appareil
exécuté pendant cet audit. Les scénarios et calculs ci-dessous sont des analyses,
pas des reproductions observées à l'écran. Les résultats CI antérieurs restent
documentés séparément dans [VALIDATION.md](VALIDATION.md).

## Avis

La structure a des responsabilités réelles, mais elle est plus compliquée que
nécessaire et contient des défauts de coordination. Le premier problème de fluidité
identifiable est le décalage entre le scroll automatique et les hauteurs animées.
Les lectures d'état par frame, les insets et les gestes méritent aussi une reprise.

L'imbrication seule ne permet pas de conclure à de mauvaises performances : Compose
gère efficacement les layouts imbriqués. L'objectif doit être de rendre explicites
les responsabilités et de limiter le travail à chaque frame. Voir les
[fondamentaux officiels des layouts Compose](https://developer.android.com/develop/ui/compose/layouts/basics).

Je recommande une refonte ciblée du rendu et de sa géométrie, en conservant la
logique métier, les proportions, les règles du weekend et l'accessibilité.
La fluidité réelle reste à mesurer ; elle ne se déduit ni de cet audit ni des
tests fonctionnels existants.

## Périmètre et repères

Les références de lignes ci-dessous concernent la révision inspectée.

| Source | Responsabilité examinée |
| --- | --- |
| [MainActivity.kt](../app/src/main/java/com/github/panlelapin/simplercal/MainActivity.kt), 185–295 | Remplacement des écrans, Scaffold, padding reçu, fond et indicateur superposé |
| [WeekLayout.kt](../app/src/main/java/com/github/panlelapin/simplercal/WeekLayout.kt), 45–329 | Formes, sept lignes, contraintes, hauteur virtuelle, scroll, branchement des gestes et proportions |
| [WeekInteraction.kt](../app/src/main/java/com/github/panlelapin/simplercal/WeekInteraction.kt), 21–241 | Sélection visuelle, interruptions, animation par frames, insets et stabilisation |
| [WeekGestures.kt](../app/src/main/java/com/github/panlelapin/simplercal/WeekGestures.kt), 20–171 | Zones tactiles, slop, consommation des événements et cycle du pointeur |
| [WeekDragPath.kt](../app/src/main/java/com/github/panlelapin/simplercal/WeekDragPath.kt), 7–59 | Distance réelle, limites, inversion et seuil Discrete |
| [DayRow.kt](../app/src/main/java/com/github/panlelapin/simplercal/DayRow.kt), 60–345 | Surface, formes, contours, deux zones internes, textes, bouton et mesure du libellé |
| [AppModels.kt](../app/src/main/java/com/github/panlelapin/simplercal/AppModels.kt), 88–173 | Transport de l'état visuel et des callbacks |
| [AppConstants.kt](../app/src/main/java/com/github/panlelapin/simplercal/AppConstants.kt), 18–35 | Poids, durée, contours, espacements et bandes périphériques |

L'ordre des modifiers est déterminant. La version déclarée est Material 3 `1.4.0`.
L'ordre interne de `Surface` a aussi été inspecté dans le bytecode de cette version
présente dans le cache Gradle, sans compiler le projet : la variante cliquable place
le traitement de surface avant `clickable`, et le traitement de surface finit par
`clip(shape)`. Le [source officiel AndroidX](https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/Surface.kt)
illustre ce mécanisme ; sa branche principale ne constitue pas le verrou de version
du projet.

## Arbre actuel et utilité des conteneurs

Arbre logique simplifié : les fonctions composables ne correspondent pas toutes à
des nœuds supplémentaires. `Surface` utilise elle-même un `Box` en interne.

```text
Scaffold
└── Surface : padding du Scaffold et fond périphérique
    └── Box : superposition de la semaine et du chargement
        └── BoxWithConstraints : choix hauteur normale / virtuelle
            └── Row : propriétaire des gestes de loupe
                ├── Spacer : bande visuelle gauche
                ├── Box : largeur restante et protection du bas
                │   └── Column : scroll éventuel et sept poids
                │       └── Surface du jour × 7 : forme et clic
                │           └── Box : dessin du contour après le contenu
                │               └── Row : réservation du contour
                │                   ├── Surface gauche rectangulaire
                │                   │   └── Box
                │                   │       └── Column
                │                   │           └── Text du jour
                │                   └── Surface droite rectangulaire
                │                       └── Box
                │                           ├── Spacer des vacances
                │                           └── Column des titres / bouton
                └── Spacer : bande visuelle droite
```

Le `Box` de chargement, le viewport de scroll, la répartition des jours et les deux
zones du panneau ont une utilité. En revanche, le `Box` puis la `Column` à gauche
ne servent qu'à aligner un seul texte. Un conteneur avec un alignement explicite
suffit. Les surfaces internes n'ont ni élévation ni interaction indépendante et
portent le même fond : de simples conteneurs avec fond suffisent probablement.
Leurs clips rectangulaires sont supplémentaires au clip du parent.

Le `Box` droit sert à superposer la bande de vacances. Il peut rester, ou la bande
peut devenir un dessin décoratif. Sa suppression ne doit pas transformer la bande
en espace extérieur ni modifier son placement dans la zone droite.

## Inventaire des espacements

| Espace | Origine actuelle | Diagnostic |
| --- | --- | --- |
| Haut et côtés de l'écran | `Scaffold` puis `padding(innerPadding)` | Justifié : barre standard et zone horizontale sûre |
| Bandes latérales | `22,5 % × max(24 dp, mandatory.right)` de chaque côté | Choix produit explicite ; 5,4 dp si le minimum de 24 dp domine |
| Bande basse | `max(72 % × mandatory.bottom, navigation.bottom, safeDrawing.bottom)` | Justifiée ; prise en charge gestuelle incomplète, voir A02 |
| Bord intérieur du panneau | 1,5 dp à gauche/droite ; haut/bas selon le contour | Réserve le trait ; ce n'est pas une marge arbitraire entre jours |
| Libellé | 8 dp de chaque côté et 2 dp en haut si développé | Les 8 dp sont nommés ; les 2 dp restent un réglage local non nommé |
| Titres | 8 dp à gauche/droite ; 3 dp de plus à gauche pendant les vacances | Évite le recouvrement de la bande de 3 dp |
| Arrondi | 10 dp sur le parent, coins spécifiques samedi/dimanche | Conforme à la séparation parent arrondi / enfants carrés |
| Minimum virtuel | `48`, `4` et `52` dans le calcul de hauteur | Mélange de cible tactile et d'estimations du contenu ; voir A09 |

Je n'ai pas identifié de double application actuelle de l'inset horizontal :
Scaffold l'applique et les bandes de semaine n'ajoutent que leur marge visuelle.
En revanche, `padding(innerPadding)` ne consomme pas explicitement ces insets.
Il faut documenter cette propriété avant d'introduire des modifiers d'insets chez
les descendants, afin d'éviter qu'une future correction ajoute un double padding.

## Constats prioritaires

Les priorités indiquent l'ordre recommandé : P1, défaut visuel à corriger en premier ;
P2, fiabilité ou coût récurrent ; P3, simplification ou politique à expliciter.
Aucun défaut bloquant ou crash n'est établi par cet audit.

### A01 — P1 : le scroll utilise la géométrie future pendant l'animation actuelle

**Code :** `WeekLayout.kt:181–188`, `WeekInteraction.kt:200–225`.

À chaque nouvelle sélection en repli défilant, `scrollTo()` saute immédiatement au
haut du jour calculé avec `dayWeightsFor(selectedDayIndex)`, donc les poids finaux.
L'animation conserve pourtant les poids précédents et les fait évoluer pendant
500 ms. Le scroll et la taille des panneaux utilisent ainsi deux géométries différentes.

Exemple analytique, police normale, hors arrondis au pixel : hauteur virtuelle
738,46 dp et viewport de 300 dp. Lundi/mardi sont développés. Le haut du vendredi
est à `80,5 % × H ≈ 594,46 dp`. Après un scroll manuel à 360 dp, vendredi apparaît
vers y = 234,46 dp et peut être touché. Le tap sélectionne vendredi : l'effet place
immédiatement le scroll à `26 % × H = 192 dp`. Avec les anciens poids, vendredi se
retrouve vers y = 402,46 dp, hors du viewport. Il y revient pendant l'animation.

C'est une discontinuité déduite du calcul, pas une capture réalisée sur appareil.
L'écart est très supérieur aux erreurs d'arrondi. Le défaut est particulièrement
pertinent puisque le repli défilant est fréquent sur téléphone.

**Correction recommandée :** coordonner le viewport et les limites réellement
animées, avec une politique d'ancrage explicite du jour touché. Éviter le saut vers
une position finale avant que cette géométrie existe. La restauration d'un viewport
au retour des réglages doit continuer à rester intacte.

### A02 — P2 : la bande basse est exclue des clics des jours, mais pas du drag Linear

**Code :** `WeekLayout.kt:190–229`, `WeekGestures.kt:35–58,163–171`.

Le parent gestuel couvre toute la `Row`, y compris sa bande basse. Un down dans
cette bande reçoit `touchDayIndex = -1`, comme un down dans une bande latérale.
En mode Linear, `-1` est accepté. Si Android transmet ensuite le mouvement à
Compose, le geste peut modifier le groupe développé depuis cette zone.

Le mécanisme est établi par lecture. La réception effective du geste près de la
navigation système dépend du système et reste à observer. Le contrat exclut
explicitement les cibles cliquables du bas ; il faut expliciter la même exclusion
pour les drags si cette bande doit être une vraie zone de protection.

**Correction recommandée :** distinguer trois zones : jour actif, bande latérale
autorisée en Linear, bande basse inerte. Ne pas utiliser une seule valeur `-1`
pour toutes les positions extérieures aux panneaux.

### A03 — P2 : l'effet tactile n'est pas placé sous le clip arrondi du panneau

**Code :** `DayRow.kt:85–104` et ordre interne de Material 3 `Surface`.

Le code passe un `Modifier.clickable` à une `Surface` non cliquable. Ce modifier est
extérieur au clip ajouté ensuite par la surface : l'indication tactile n'est donc
pas protégée par ce clip arrondi. Le contenu bénéficie du clip ; l'indication
extérieure peut rester rectangulaire aux coins. Le problème concerne également
la superposition de l'indication par rapport au contour dessiné par le contenu.

**Correction recommandée :** employer la surcharge Material `Surface(onClick=…,
enabled=…)`, avec la sémantique du jour conservée, ou composer explicitement
`clip → clickable → contenu` et le contour au bon niveau. La surcharge ajoute sa
propre gestion de taille interactive : vérifier son effet sur les sept hauteurs.
Ne pas conserver simultanément deux propriétaires du clic.

La non-protection par le clip est établie dans l'ordre des modifiers ; l'apparence
du ripple aux coins reste à confirmer par observation ou test d'image.

### A04 — P2 : trop de calcul et de composition dépendent de chaque frame

**Code :** `WeekLayout.kt:151–156,233–245`, `DayRow.kt:320–337`.

`animatedDayWeights` est lu dans `WeekView`, notamment pour alimenter
`rememberUpdatedState`. Cette lecture invalide ce scope à chaque changement des
poids. `rememberUpdatedState` maintient une valeur à jour pour les callbacks ; il
n'évite pas la lecture en composition de son argument.

La largeur du libellé est alors redemandée aux sept textes ; le style et les textes
annotés sont reconstruits. `TextMeasurer` possède un cache : cela ne signifie pas
sept compositions typographiques complètes à chaque frame. Les appels, recherches
et allocations restent néanmoins répétés alors que les dates ne changent pas.
Les sept `DayRowState` sont aussi reconstruits ; le détail du skipping dépend de
la stabilité des paramètres et du compilateur, et n'a pas été mesuré ici.

**Correction recommandée :** mémoriser la largeur selon les libellés et les
paramètres typographiques pertinents, isoler le contenu stable, et lire les poids
au plus près de la mesure. Une fonction de lecture appelée depuis un `Layout`
peut laisser les changements de taille en phase de layout au lieu de recomposer
la vue entière. Le calcul des groupes autorisés peut également être mémorisé.

Il faut réellement changer les hauteurs : remplacer ce travail par un simple
`graphicsLayer.scaleY` déformerait les textes et désynchroniserait les interactions.
Cette recommandation suit la [séparation des phases Compose](https://developer.android.com/develop/ui/compose/performance/phases)
et les [lectures d'état différées](https://developer.android.com/develop/ui/compose/performance/bestpractices).
Le coût est identifiable ; des frames perdues ne sont pas démontrées.

### A05 — P2 : les valeurs d'insets sont matérialisées trop tôt

**Code :** `WeekInteraction.kt:166–192`, `WeekLayout.kt:176–195`.

Les `calculateBottomPadding()` et `calculateRightPadding()` sont lus pendant la
composition, convertis en dp et pixels, puis utilisés pour dimensionner la semaine
et ses gestes. Android précise que les insets sont mis à jour après la composition
et avant le layout : leur lecture en composition utilise généralement une valeur
en retard d'une frame lors d'une variation.
Voir [insets et phases Compose](https://developer.android.com/develop/ui/compose/system/insets-ui#insets-and-jetpack-compose-phases).

**Correction recommandée :** garder les formules produit, mais résoudre les valeurs
en mesure via un modifier adapté ou un propriétaire de géométrie unique. Les limites
transmises aux gestes doivent correspondre aux limites effectivement mesurées.
Ajouter simplement un `navigationBarsPadding()` à l'existant doublerait potentiellement
la protection au lieu de résoudre le problème de phase.

### A06 — P2 : une modification de hauteur peut laisser un drag sur l'ancienne échelle

**Code :** `WeekGestures.kt:20–33,37,59–64,123–124`, `WeekDragPath.kt:14–19`.

La hauteur est capturée au down et la conversion pixels/focus reste fondée sur
cette hauteur. Elle n'est pas une clé de `pointerInput`. Si la fenêtre est
redimensionnée pendant le geste tout en restant dans le mode sans scroll et avec
les mêmes insets, les panneaux utilisent la nouvelle hauteur mais le drag conserve
l'ancienne distance de transition.

Un changement de semaine avec le même masque de jours actifs ne redémarre pas
non plus le gestionnaire. Un geste en cours peut donc continuer dans un nouveau
contexte. Les callbacks actualisés limitent les fermetures obsolètes, mais ne
définissent pas la politique d'annulation du geste précédent.

**Correction recommandée :** invalider ou rebaser le geste lorsque sa géométrie
mesurée ou l'identité de semaine change. Ne pas utiliser les poids animés comme
clé : cela annulerait le geste à chacune de ses propres mises à jour. Ces parcours
ne sont pas reproduits sur appareil dans cet audit.

### A07 — P2 : calcul du toucher et placement ne partagent pas leurs limites finales

**Code :** `WeekLayout.kt:232,310–328`, `WeekGestures.kt:47–51`,
`WeekDragPath.kt:55–58`.

`Column.weight` répartit des hauteurs entières en pixels ; le détecteur recalcule
des limites flottantes à partir des proportions, avec en plus un `toInt()` de la
hauteur utile. Il existe donc deux méthodes de calcul des mêmes frontières.
Près d'une jointure, leurs arrondis peuvent attribuer le down au jour voisin ou
modifier légèrement le seuil Discrete. L'écart exact n'a pas été mesuré.

**Correction recommandée :** définir un arrondi cumulatif déterministe et partager
les mêmes limites de lignes entre mesure, hit-test, seuil et ancrage du scroll.
Les taps doivent continuer à appartenir aux enfants, pas à un deuxième détecteur
parent. Un layout spécialisé devient utile s'il supprime réellement cette duplication.

### A08 — P3 : l'annulation rétablit une cible, pas la géométrie interrompue

**Code :** `WeekInteraction.kt:65–72,110–117`.

Le début de drag mémorise les poids courants. En revanche, `cancelDrag()` rétablit
directement `dayWeightsFor(dragOriginSelectedDay)`. Si le drag a interrompu une
animation de tap à mi-parcours, l'annulation peut produire un saut vers la cible
qui n'était pas encore atteinte au début du geste.

Le nettoyage de l'état est présent et le contrat impose une annulation sûre, sans
définir précisément sa trajectoire visuelle. Ce n'est donc pas une violation
contractuelle établie. Il faut décider et tester la politique de reprise avant
de promettre une continuité dans tous les cas.

Autre point à instrumenter : l'animation est annulée par le renouvellement de la
`LaunchedEffect`, pas par un contrôle `isDragging` dans sa boucle de frames.
Une éventuelle écriture concurrente à la prise du geste dépend de l'ordonnancement ;
aucun écrasement de poids par cette course n'est établi ici.

### A09 — P3 : hauteur minimale fondée sur une estimation et repli très fréquent

**Code :** `WeekLayout.kt:168–179,257–260`.

Le minimum de 48 dp pour 6,5 % impose `48 / 0,065 ≈ 738,46 dp` utiles à la semaine,
après barre supérieure et protection basse. En dessous, le gestionnaire de loupe
n'est plus installé : c'est un scroll vertical ordinaire, dans les deux modes.
C'est une décision explicite du contrat, pas un bug à corriger en diminuant les
cibles tactiles. Elle explique cependant pourquoi l'animation gestuelle peut être
absente dans beaucoup de fenêtres de téléphone.

L'autre minimum utilise la hauteur de ligne de `bodyMedium` et des réserves `4` et
`52` dp. Ce sont des approximations des contours, textes et bouton Material ; elles
ne sont pas dérivées des mesures réelles. Le libellé et le bouton utilisent aussi
d'autres styles. Le test actuel vérifie l'équation, pas l'absence de rognage de
neuf titres ou de huit titres plus bouton à chaque échelle de police.

**Correction recommandée :** nommer et justifier ces réserves, ou établir une
politique de mesure du contenu compatible avec la mesure Compose. Vérifier les
hauteurs réellement obtenues autour du seuil et avec le bouton. Aucun rognage à
une échelle donnée n'est affirmé sans mesure.

### A10 — P3 : responsabilités et décorations peuvent être simplifiées

**Code :** `DayRow.kt:110–155,205–315`, `WeekLayout.kt:224–232`,
`WeekInteraction.kt:146–151`.

Simplifier le libellé à un conteneur aligné ; remplacer les surfaces internes quand
leur rôle n'exige qu'un fond ; garder un seul propriétaire des coins, du contour
et de l'interaction. Le renouvellement de `updateSelectionListener` en composition
est également redondant avec le `rememberUpdatedState` déjà capturé par l'objet.

La chaîne `fillMaxSize → verticalScroll → height(weekHeight)` est sensible à l'ordre
des modifiers mais a une explication : le scroll borne le viewport et laisse une
hauteur virtuelle finie à la colonne pondérée. Je ne conclus pas à une erreur de
contraintes infinies dans la version actuelle. Il faut encapsuler ce mécanisme et
le documenter plutôt que déplacer ses modifiers sans préserver ce fonctionnement.

Le contour est recréé dans `drawWithContent`. Un `drawWithCache` ne résoudrait pas
à lui seul le coût pendant l'animation, puisque la taille change à chaque frame.
Toute optimisation de ce dessin doit être guidée par une trace.

## Choix corrects à conserver

- Un seul contour combiné est dessiné après les enfants. La réservation intérieure
  protège le trait ; la jointure ordinaire appartient à une seule ligne. Les
  exceptions samedi/dimanche et jour courant sont explicites.
- Les deux zones internes ont la même hauteur, sans arrondi indépendant. Les poids
  donnent cinq jours à 6,5 % et deux à 33,75 % au repos.
- Une progression temporelle partagée évite sept animations qui dériveraient.
  `withFrameNanos` répond au contrat de 500 ms indépendantes de l'échelle Android.
  Le résultat final arrive à la première frame disponible atteignant cette durée ;
  ce mécanisme ne garantit pas à lui seul une livraison régulière des frames.
- Le drag capture les poids réellement affichés, traite le dernier déplacement up,
  borne la distance sans accumuler d'overscroll et nettoie les annulations.
- Les clics appartiennent aux enfants, le parent arbitre les drags ; dépasser le
  slop annule le clic même si Discrete ne peut pas commencer depuis ce jour.
- Les contenus sortants sont gardés jusqu'à la fin du tap ; les contenus entrants
  apparaissent dès la sélection. Les labels et les titres utilisent volontairement
  deux indicateurs d'expansion différents.
- Le retour de sélection du ViewModel ne relance pas normalement le tap : la
  demande explicite de sélection est distincte du simple retour métier.
- Le détail des événements est accessible par une action du jour ; le bouton
  conserve son action tactile sans second nœud accessible. Les réglages remplacent
  la composition de semaine. Ces propriétés doivent survivre à la simplification.

## Couverture existante et vérifications manquantes

Lecture de [WeekGestureTest](../app/src/androidTest/java/com/github/panlelapin/simplercal/WeekGestureTest.kt),
[WeekViewTest](../app/src/androidTest/java/com/github/panlelapin/simplercal/WeekViewTest.kt),
[DayRowAccessibilityTest](../app/src/androidTest/java/com/github/panlelapin/simplercal/DayRowAccessibilityTest.kt),
[CalendarFlowTest](../app/src/androidTest/java/com/github/panlelapin/simplercal/CalendarFlowTest.kt),
[WeekLogicTest](../app/src/test/java/com/github/panlelapin/simplercal/WeekLogicTest.kt)
et [RegressionTest](../app/src/test/java/com/github/panlelapin/simplercal/RegressionTest.kt).

Les huit tests de gestes utilisent un montage `Box + Column + DayRow` avec insets
nuls et largeur de libellé fixée. Les quatre tests `WeekViewTest` imposent 400 dp,
ou 220 dp avec police 200 % : ils empruntent le repli défilant. Les parcours
applicatifs testent notamment les réglages et le détail, sans imposer un scénario
de drag intégré avec hauteur suffisante et bandes non nulles.

| Vérification à ajouter lors du chantier de correction | Preuve attendue |
| --- | --- |
| Vraie `WeekView` assez haute, Discrete et Linear | Drag intégré avec les mêmes limites et hauteurs que l'affichage |
| Tap vendredi après scroll manuel, horloge arrêtée frame par frame | Le jour touché reste visible ; absence du saut A01 |
| Insets non nuls, down au centre des bandes gauche/droite/basse | Bandes latérales Linear actives ; bas inerte si cette politique est retenue |
| Coins et contours, état pressé, clair/sombre, jours normaux et weekend | Indication découpée par la forme et contour lisible, sans doublon de seam |
| Hauteurs juste sous/au-dessus du minimum, densités fractionnaires | Pas de mode oscillant ni cible attribuée au voisin à la jointure |
| Resize pendant un drag et changement de semaine avec second pointeur | Annulation ou rebasage défini, aucun geste sur un contexte périmé |
| Tap A → B → C, puis prise du drag et annulation | Poids continus selon la politique retenue, bonne sélection finale |
| 0/1/9/10/100 événements, grandes polices | Textes et bouton réellement atteignables, détail tactile et accessible conservé |
| Limites d'année, dimanche sélectionné, retour des réglages | Jours interdits inactifs, identité de dimanche et viewport préservés |
| Animation à frames espacées, échelle Android 0 / 0,5 / 1 / 10 | Temps écoulé conforme au contrat ; une seule progression et pas de replay |

Les tests de temps virtuel vérifient l'état et la trajectoire. Ils ne prouvent pas
que le téléphone tient sa cadence. Aucun module Macrobenchmark n'est défini dans
le projet inspecté.

Pour conclure sur « tout glisse parfaitement », mesurer un parcours déterministe
sur une variante proche de Release, non débogable et profilable, avec événements
déjà chargés : taps successifs, drag lent/rapide, interruption et repli défilant.
Collecter `FrameTimingMetric`, les percentiles P95/P99 de durée et de dépassement,
et une trace Perfetto pour identifier composition, mesure et dessin. Comparer
avant/après sur le même appareil, à 60 et 120 Hz lorsque disponibles ; les budgets
indicatifs sont respectivement 16,7 et 8,3 ms par frame. Une référence sur appareil
physique est nécessaire pour conclure sur la fluidité ressentie ; la CI émulateur
peut surtout protéger les invariants fonctionnels.
Références : [métriques Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics)
et [configuration d'un benchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview).

## Ordre de correction recommandé

1. Corriger la coordination scroll/animation et séparer les zones du geste ;
   verrouiller ces deux comportements avec des tests ciblés.
2. Corriger le propriétaire du clic et du clip, avec la sémantique et le contour
   préservés. Simplifier le libellé et les surfaces décoratives dans cette zone.
3. Mémoriser les mesures stables et les groupes actifs ; réduire la portée des
   lectures d'état animées sans changer la mécanique métier.
4. Centraliser la géométrie : viewport utile, bandes, hauteur virtuelle, limites
   arrondies des sept jours. La mesure, les gestes et le scroll utilisent cette
   même définition. Un petit `Layout` spécialisé est pertinent pour ce partage ;
   un gestionnaire de rendu général ou une migration vers ConstraintLayout
   n'apporterait pas de bénéfice établi.
5. Définir l'annulation/reprise, couvrir les changements de contexte et confronter
   la hauteur minimale aux mesures réelles du contenu.
6. Après délégation explicite, exécuter les contrôles suivis puis les parcours
   instrumentés et les mesures de fluidité. Reporter séparément code corrigé,
   tests réussis, comportement visuellement confirmé et performance mesurée.

Le rapport constitue un diagnostic et une proposition de chantier. Il n'accorde
aucune validation ni autorisation permanente de build, livraison ou installation.

## Suite : reprise implémentée

| Constat | Traitement dans le worktree |
| --- | --- |
| A01 | Ancrage du jour selon les limites courantes ; poids et ScrollState publiés atomiquement ; priorité au scroll manuel |
| A02 | Bande basse exclue dès le down, sans consommation de ses événements ; bandes latérales conservées |
| A03 | Surface(onClick, enabled), clip Material et contour extérieur dessiné après l’indication |
| A04 | Poids lus dans le MeasurePolicy ; largeur des libellés mémorisée et groupes actifs mémorisés |
| A05 | Résolution des insets pendant la mesure et consommation explicite des paddings du Scaffold |
| A06 | Géométrie et lundi parmi les clés du détecteur ; interaction reconstruite au changement de semaine |
| A07 | Limites cumulatives arrondies communes au placement, hit-test, seuil et chemin de drag |
| A08 | Aucun saut de poids à l’annulation ; reprise vers l’identité initiale après le geste ; garde de génération dans la boucle de frames |
| A09 | Sondes réelles du texte normal/italique et du bouton Material ; métriques du libellé et réservation des traits |
| A10 | Suppression des surfaces internes, des boîtes superflues et du renouvellement redondant du listener ; fond unique du jour et bande de vacances dessinée |

L’identité de dimanche est également préservée après sortie puis retour au weekend
pendant un même drag. 30 tests ajoutés couvrent les nouvelles frontières et les
interruptions. Leur définition, leur compilation et leur exécution restent séparées
dans TESTS.md et VALIDATION.md. Aucun résultat de fluidité physique n’est revendiqué.

Le premier contrôle local de cette reprise avait réussi avec 87 tests JVM,
18 Python et compilation de 48 tests Android. Le contrôle commun ultérieur aux
panneaux et aux créneaux réussit avec 110 JVM, 18 Python et compilation des
55 tests Android ; voir VALIDATION.md pour les preuves locales/distantes datées.
Le trait ordinaire est un chemin ouvert, sans masque supprimant les côtés à la
jointure du weekend ; le contour courant suit exactement le rayon extérieur.
Les résultats des tests d'image et de gestes intégrés Android sont consignés dans
VALIDATION.md. Le protocole de mesure physique est documenté dans PERFORMANCE.md,
sans résultat inventé.
