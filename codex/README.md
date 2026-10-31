# Reprendre SimplerCal

Ce répertoire contient les instructions nécessaires pour comprendre, modifier ou
réimplémenter le projet sans disposer des anciennes conversations ni de la mémoire
d'un agent. Les sources suivies par Git restent la preuve de l'implémentation réelle.

## Ordre de lecture

1. [CONTRACT.md](CONTRACT.md) : contrat complet, obligatoire. Comportement attendu,
   interface, gestes, calendrier, sauvegarde, thèmes et règles d'intervention.
2. [ARCHITECTURE.md](ARCHITECTURE.md) : carte du code, flux et état persistant/transitoire.
3. [DEVELOPMENT.md](DEVELOPMENT.md) : nouvelle machine, commande unique, GitHub,
   signature, CI, récupération et dépannage.
4. [VALIDATION.md](VALIDATION.md) : preuves connues, portée des tests, scénarios manuels
   et règles de transmission au prochain agent.
5. [DECISIONS.md](DECISIONS.md) : raisons des choix structurants et frontières à préserver.
6. [TESTS.md](TESTS.md) : inventaire des tests ajoutés, répartition local/CI et limites encore manuelles.

Créneaux quotidiens : [SCHEDULES.md](SCHEDULES.md), définition des cinq heures,
affectation des événements aux lignes, limites et règles de reprise.

Audit ciblé : [panneaux Compose](AUDIT_PANNEAUX_COMPOSE.md), structure, espacements,
animation, gestes et corrections proposées ; diagnostic statique puis reprise du 31 octobre 2026.
[PERFORMANCE.md](PERFORMANCE.md) décrit les parcours et mesures de fluidité restant
à effectuer sur appareil.

## Autorité et périmètre

Les instructions explicites de l'utilisateur priment. AGENTS.md impose la lecture
du contrat complet ici. CONTRACT.md définit le produit ; les autres documents
expliquent son implémentation et son exploitation. En cas de contradiction, appliquer
le contrat puis signaler/corriger la documentation divergente sans inventer de règle.
Le snapshot de skill est un outillage réutilisable, pas une autre spécification produit.

Les fichiers PB1.md et PB2.md à la racine sont des audits historiques, avec des
statuts datés. [MATERIAL.md](MATERIAL.md) précise les rôles de couleur. Aucun de ces documents ne
remplace les résultats d'un contrôle exécuté sur le commit actuel.

Tout est relatif à la racine du clone. Aucun chemin personnel de l'auteur, aucune
clé privée ni conversation externe ne sont requis pour comprendre l'application.
L'authentification GitHub, le JDK/SDK, les secrets Actions et les données calendrier
Android sont les dépendances externes décrites dans DEVELOPMENT.md.
