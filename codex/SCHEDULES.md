# Les cinq créneaux quotidiens

Cette spécification remplace l'ancien affichage « un événement = une ligne ».
L'interface reste en anglais. Les créneaux sont communs aux sept journées.

## Définition dans les réglages

Les cinq valeurs existantes `schedule_time_0` à `schedule_time_4` sont conservées.
Aucune migration destructive, heure par défaut ou alarme n'est ajoutée.

| Ligne | Réglage | Intervalle |
| --- | --- | --- |
| 1 | Until H1 | [00:01, H1[ |
| 2 | From H2 | [H2, H3[ |
| 3 | From H3 | [H3, H4[ |
| 4 | From H4 | [H4, H5[ |
| 5 | From H5 | [H5, minuit du lendemain[ |

H1 est strictement après 00:01, H1 ≤ H2, puis H2 < H3 < H4 < H5.
H1 et H2 peuvent être égaux. S'ils diffèrent, la pause entre eux n'est couverte
par aucune ligne. Les réglages affichent une explication et signalent les valeurs
incomplètes ou non chronologiques. Les valeurs restent modifiables indépendamment,
afin de pouvoir corriger un ancien ensemble désordonné sans perdre ses heures.

L'affectation commence lorsque les cinq valeurs sont complètes et cohérentes.
En attendant, les cinq positions et leurs heures restent affichées ; aucun ancien
résultat d'affectation n'est réutilisé. Le détail des événements reste disponible.

## Répartition des événements

Les intervalles sont comparés avec une fin exclusive : un événement finissant
exactement au début du créneau suivant ne touche pas celui-ci. Les événements sont
d'abord projetés sur les créneaux qu'ils recoupent réellement, puis on retire le
dernier s'ils en recoupent plusieurs. Cela donne la règle confirmée par l'utilisateur :

| Créneaux traversés dans l'ordre | Lignes recevant le titre |
| --- | --- |
| 2 | 2 |
| 2, 3 | 2 |
| 2, 3, 4 | 2, 3 |
| 1, 2, 3, 4 | 1, 2, 3 |
| 1, 2, 3, 4, 5 | 1, 2, 3, 4 |

La règle s'applique à la durée entière. Un événement commençant lundi dans le
créneau 5 et finissant mardi dans le créneau 4 garde lundi 5 et mardi 1/2/3.
Il ne faut pas retirer lundi 5 comme si chaque journée était un nouvel événement.
Sur une journée intermédiaire d'un événement plus long, les cinq lignes peuvent
donc recevoir le titre. Les dates de début/fin originales et les identifiants sont
conservés dans le détail, sans créer de copies d'événement dans le calendrier.

Les événements journée entière restent des dates UTC flottantes, indépendantes
du fuseau d'affichage. Une journée entière isolée touche les cinq créneaux et est
donc affichée sur les quatre premiers suivant la même règle. Les événements horaires
utilisent le referenceZone du ViewModel, également utilisé dans le détail.

Lors des changements d'heure, les plages locales sont intersectées avec les
segments d'offset constant du jour. Un créneau inexistant n'est pas inventé ; les
deux occurrences d'une heure répétée sont prises en compte. Les fenêtres contiguës
du même créneau sont fusionnées ; un événement n'est ajouté qu'une fois à une ligne.
Ce traitement s'appuie sur les [règles Java de fuseau et de transition](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/zone/ZoneRules.html).

Une durée nulle est un événement ponctuel. Une durée inversée n'est pas projetée.
Un événement limité à 00:00–00:01 ou entièrement dans la pause H1–H2 ne remplit
aucun créneau mais reste dans la liste complète du jour.

## Affichage et accès au détail

Un jour développé conserve toujours ses cinq lignes, y compris les créneaux vides.
Les libellés sont `Until HH:mm` puis quatre `From HH:mm`. Le titre du premier
événement de la ligne suit ce libellé. S'il y en a plusieurs : `Titre (+N)` ; aucun
tri ni identifiant du calendrier n'est remplacé. La liste complète conserve chaque
instance originale une seule fois et affiche ses titres et heures sans ce résumé.

Le jour compact montre le premier créneau occupé, ou le premier créneau s'il n'y a
pas d'affectation. Tout jour ayant des événements propose All events en mode
développé et une action d'accessibilité sur le jour, même si moins de dix événements
existent ou si les réglages sont incomplets. Ce bouton est une commande distincte
des cinq créneaux, pas un sixième créneau. Aucun appui long n'est réintroduit.

## Code et reprise

- ScheduleSlots.kt : validation, cinq lignes, projection et fenêtres de fuseau.
- WeekLayout.kt : snapshot d'entrée, calcul sur Dispatchers.Default via produceState,
  annulation et rejet d'un ancien contexte ; aucune lecture des fichiers sources.
- MainActivity.kt / AppModels.kt : transmission des cinq heures et de referenceZone.
- DayRow.kt : affichage des lignes et accès permanent au détail des jours non vides.
- WeekViewport.kt : mesure réelle de cinq lignes et de cinq lignes plus bouton,
  dans les styles normal/italique ; proportions et animation des panneaux conservées.
- SettingsScreen.kt : premier sélecteur Until, quatre From, erreurs de configuration
  visibles et libellés accessibles complets pour chaque bouton d'heure.

Le cache de fenêtres appartient à une seule projection de semaine. Les boucles
vérifient l'annulation. Les extrémités d'un événement long sont recherchées sur
leurs dates et les dates voisines ; aucune boucle sur les années de sa durée.
La projection publiée porte son entrée (semaine, données, heures, fuseau), qui doit
toujours correspondre à l'entrée courante avant affichage.

Contrôle local des panneaux et des créneaux réussi le 1er novembre 2026 :
110 tests JVM et 18 Python, Detekt et compilation des 55 tests Android.
L'exécution Android et l'APK sont consignés séparément dans VALIDATION.md.
