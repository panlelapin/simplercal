# Vérification de la fluidité des panneaux

La refonte supprime des causes identifiées de sauts et de travail en composition.
Elle ne constitue pas une mesure de fluidité sur appareil. Les tests à horloge
contrôlée vérifient les états, la durée et la trajectoire ; les émulateurs CI
protègent les comportements fonctionnels. Un résultat physique reste nécessaire.

## Parcours reproductible

Utiliser la même version Android, le même appareil, la même fréquence d'écran,
le même thème, la même taille de police et les mêmes données pour avant/après.
Précharger les calendriers ; ne pas inclure leur import dans la fenêtre mesurée.
Consigner ces paramètres, la révision, la variante et les dimensions utiles.

1. Configurer les cinq créneaux ; une semaine avec neuf événements répartis entre
   eux, dont plusieurs dans une même ligne, puis une semaine avec
   cent événements sur un jour pour inclure All events. Vérifier le détail à part.
2. En fenêtre assez haute pour la loupe : dix taps alternant lundi/vendredi,
   dix drags lents puis rapides, inversions aux limites, prise de drag pendant un
   tap, annulation et changement de dimensions pendant le geste.
3. En repli défilant : scroll manuel jusqu'à vendredi, sélection de vendredi,
   nouveau scroll pendant son animation, retour des réglages et grandes polices.
4. Répéter à 60 et 120 Hz si disponibles, et en navigation par gestes puis boutons.

Vérifier visuellement : aucun jour touché qui disparaît, effet tactile découpé par
les coins, contour courant toujours lisible, joints sans double trait, vacances
dans le bon conteneur et textes/bouton atteignables. TalkBack est un parcours séparé.

## Traces et chiffres

Pour une comparaison fiable, utiliser une variante proche de Release, non débogable
et profilable, et Macrobenchmark avec FrameTimingMetric. La variante de production
actuelle n'est pas rendue profilable par cette refonte. Aucun module Macrobenchmark
n'est actuellement défini ; ces mesures restent à effectuer sur un appareil.
La préparation d'une variante et son installation restent soumises au contrat de
livraison, sans assemblage APK local ni remplacement de l'application sans autorisation.

Recueillir P50/P95/P99 de frameDurationCpuMs et frameOverrunMs, garder les traces
Perfetto et examiner les frames lentes : composition, mesure/layout, dessin, RenderThread
et GPU. Comparer des séquences identiques et échauffées ; une frame maximale isolée
ou une moyenne ne suffit pas. Les budgets indicatifs sont 16,7 ms à 60 Hz et 8,3 ms
à 120 Hz. Le test Compose démontrant l'absence de recomposition des contenus pendant
une modification des poids ne remplace pas ces mesures de durée.

Références officielles : [Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview),
[métriques](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics),
[phases Compose](https://developer.android.com/develop/ui/compose/performance/phases).

## État des preuves

Aucune trace Perfetto, mesure physique de cadence ou validation visuelle de cette
refonte n'a été réalisée. Ne pas annoncer « 60/120 FPS garantis » à partir des tests
fonctionnels ou de la seule lecture du code. Reporter ici appareil, révision,
parcours, traces, chiffres et limites lorsque ces observations seront disponibles.
