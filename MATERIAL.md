# Material 3 — rôles de couleur

Ce document décrit les rôles `ColorScheme` utilisés par Material 3 dans Jetpack Compose.

## Règles générales

- Toujours utiliser `MaterialTheme.colorScheme.<role>` plutôt qu'une couleur fixe.
- Un rôle `onX` est destiné au texte, aux icônes et aux contrôles placés sur la couleur `X`.
  Par exemple, du texte placé sur `secondaryContainer` utilise généralement
  `onSecondaryContainer`.
- Les composants Material 3 doivent être utilisés avec leurs couleurs par défaut lorsque cela
  convient. Ils appliquent déjà les rôles et les états Material correctement.
- `App bar background` n'est pas un rôle Material officiel. Dans SimplerCal, c'est un alias local
  désignant explicitement le rôle `surfaceContainer` utilisé par les barres d'application et les
  zones périphériques.
- `secondary` est la couleur d'accent secondaire. Pour une version plus douce ou plus adaptée à
  un grand fond, utiliser `secondaryContainer` plutôt que fabriquer une couleur pastel.

## Accents principaux

### Primary

- `primary` : couleur principale, utilisée pour les actions importantes, la sélection et les
  éléments actifs.
- `onPrimary` : contenu placé sur `primary`.
- `primaryContainer` : fond tonal mis en avant pour un composant principal.
- `onPrimaryContainer` : contenu placé sur `primaryContainer`.

### Secondary

- `secondary` : accent secondaire, moins dominant que `primary`.
- `onSecondary` : contenu placé sur `secondary`.
- `secondaryContainer` : fond tonal secondaire, généralement plus doux que `secondary`.
- `onSecondaryContainer` : contenu placé sur `secondaryContainer`.

### Tertiary

- `tertiary` : troisième accent, utile pour équilibrer `primary` et `secondary` ou attirer
  l'attention sur un élément distinct.
- `onTertiary` : contenu placé sur `tertiary`.
- `tertiaryContainer` : fond tonal tertiaire.
- `onTertiaryContainer` : contenu placé sur `tertiaryContainer`.

## Surfaces et fonds

- `background` : fond derrière le contenu défilant.
- `onBackground` : contenu placé sur `background`.
- `surface` : surface standard de composants comme les cartes, feuilles et menus.
- `onSurface` : contenu principal placé sur `surface`.
- `surfaceVariant` : autre variante de surface.
- `onSurfaceVariant` : contenu secondaire placé sur une surface variante ou une surface de
  conteneur.
- `surfaceDim` : surface toujours plus sombre que `surface`, en mode clair comme en mode sombre.
- `surfaceBright` : variante de surface plus lumineuse.
- `surfaceContainerLowest` : conteneur de surface avec le niveau d'emphase le plus faible.
- `surfaceContainerLow` : conteneur de surface avec une faible emphase.
- `surfaceContainer` : conteneur de surface standard pour les grandes zones et composants.
- `surfaceContainerHigh` : conteneur de surface avec une emphase supérieure.
- `surfaceContainerHighest` : conteneur de surface avec l'emphase la plus élevée.
- `surfaceTint` : teinte utilisée par les composants qui appliquent une élévation tonale.

Dans SimplerCal, la correspondance actuelle est :

- `app bar background` = `surfaceContainer` ;
- jour de base = `surface` / `onSurface` ;
- weekend et jour férié = `surfaceContainer` / `onSurface` ;
- jour passé = `surfaceDim` / `onSurfaceVariant` ;
- jour passé et weekend ou férié = `surfaceContainer` / `onSurface` ;
- vacances : barre verticale `primary` ;
- jour courant : liseré `primary`.

Dans l'implémentation de test, lundi et mardi sont des jours fériés et mardi est aussi un jour
férié bancaire (`isBankH`). Material 3 ne définit pas de rôle `onSurfaceContainer` : le rôle
officiel de contenu pour `surfaceContainer` est `onSurface`.

## Contours et effets

- `outline` : contour principal, avec davantage de contraste.
- `outlineVariant` : contour ou séparateur secondaire, plus discret ; c'est le rôle adapté aux
  séparateurs décoratifs qui ne doivent pas dominer l'interface.
- `scrim` : voile utilisé au-dessus du contenu, notamment lorsqu'un élément modal le recouvre.

## États inversés

- `inverseSurface` : surface fortement contrastée par rapport à `surface`.
- `inverseOnSurface` : contenu lisible placé sur `inverseSurface`.
- `inversePrimary` : variante de `primary` utilisée dans un contexte de schéma inversé, par
  exemple certains contrôles de Snackbar.

## Erreurs

- `error` : couleur signalant une erreur ou un état invalide.
- `onError` : contenu placé sur `error`.
- `errorContainer` : fond tonal pour un message ou composant d'erreur.
- `onErrorContainer` : contenu placé sur `errorContainer`.

## Rôles fixes et variantes de version

Les versions récentes de Material 3 ajoutent aussi les rôles fixes. Ils conservent une tonalité
stable entre les thèmes clair et sombre :

- `primaryFixed`, `primaryFixedDim`, `onPrimaryFixed`, `onPrimaryFixedVariant` ;
- `secondaryFixed`, `secondaryFixedDim`, `onSecondaryFixed`, `onSecondaryFixedVariant` ;
- `tertiaryFixed`, `tertiaryFixedDim`, `onTertiaryFixed`, `onTertiaryFixedVariant`.

Ils ne doivent être utilisés que lorsqu'une couleur stable entre les thèmes est réellement
nécessaire. Pour l'interface courante de SimplerCal, les rôles dynamiques ordinaires restent
préférables.

## Raccourci de choix

- Action principale ou sélection : `primary` / `onPrimary`.
- Accent secondaire : `secondary` / `onSecondary`.
- Fond tonal doux : `secondaryContainer` / `onSecondaryContainer`.
- Texte normal sur une surface : `onSurface`.
- Texte secondaire : `onSurfaceVariant`.
- Fond d'écran ou grande zone : `surface` ou un rôle `surfaceContainer*`.
- Barre d'application : `surfaceContainer`.
- Séparateur discret : `outlineVariant`.
- Contour visible : `outline`.
- Erreur : `error` / `onError` ou `errorContainer` / `onErrorContainer`.

## Sources officielles

- [Material 3 dans Jetpack Compose — Android Developers](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [ColorScheme — Android Developers](https://developer.android.com/reference/kotlin/androidx/compose/material3/ColorScheme)
