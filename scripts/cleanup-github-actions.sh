#!/usr/bin/env bash
#
# Nettoyage GitHub Actions pour le compte panlelapin
#
# Effets :
#   1. recense les artefacts GitHub Actions de tous les dépôts publics ;
#   2. affiche le volume trouvé ;
#   3. demande une confirmation explicite ;
#   4. supprime tous les artefacts trouvés ;
#   5. fixe à 1 jour la rétention future des artefacts et journaux.
#
# Prérequis :
#   - GitHub CLI : https://cli.github.com/
#   - connexion : gh auth login
#
# Usage :
#   chmod +x cleanup-github-actions.sh
#   ./cleanup-github-actions.sh
#
# Autre compte ou autre durée :
#   ./cleanup-github-actions.sh NOM_DU_COMPTE 3
#

set -uo pipefail

OWNER="${1:-panlelapin}"
RETENTION_DAYS="${2:-1}"
API_VERSION="2026-03-10"
TMP_FILE="$(mktemp)"
trap 'rm -f "$TMP_FILE"' EXIT

if ! command -v gh >/dev/null 2>&1; then
  echo "Erreur : GitHub CLI (gh) n'est pas installé." >&2
  echo "Installation : https://cli.github.com/" >&2
  exit 1
fi

if ! gh auth status >/dev/null 2>&1; then
  echo "Erreur : tu n'es pas connecté à GitHub CLI." >&2
  echo "Exécute d'abord : gh auth login" >&2
  exit 1
fi

if ! [[ "$RETENTION_DAYS" =~ ^[0-9]+$ ]] || (( RETENTION_DAYS < 1 || RETENTION_DAYS > 90 )); then
  echo "Erreur : la rétention doit être comprise entre 1 et 90 jours pour des dépôts publics." >&2
  exit 1
fi

echo "Compte GitHub : $OWNER"
echo "Rétention future : $RETENTION_DAYS jour(s)"
echo
echo "Recherche des dépôts publics…"

mapfile -t REPOS < <(
  gh repo list "$OWNER" \
    --visibility public \
    --limit 1000 \
    --json nameWithOwner \
    --jq '.[].nameWithOwner'
)

if (( ${#REPOS[@]} == 0 )); then
  echo "Aucun dépôt public trouvé pour $OWNER."
  exit 0
fi

echo "${#REPOS[@]} dépôt(s) trouvé(s)."
echo "Inventaire des artefacts…"
echo

TOTAL_COUNT=0
TOTAL_BYTES=0

for repo in "${REPOS[@]}"; do
  REPO_FILE="$(mktemp)"

  if gh api --paginate \
      -H "X-GitHub-Api-Version: $API_VERSION" \
      "repos/$repo/actions/artifacts?per_page=100" \
      --jq '.artifacts[] | [.id, .size_in_bytes, .name, .created_at] | @tsv' \
      > "$REPO_FILE" 2>/dev/null; then

    count="$(awk 'NF {n++} END {print n+0}' "$REPO_FILE")"
    bytes="$(awk -F $'\t' 'NF {s += $2} END {printf "%.0f", s+0}' "$REPO_FILE")"

    if (( count > 0 )); then
      printf "%-55s %6d artefact(s), %10.2f MiB\n" \
        "$repo" "$count" "$(awk -v b="$bytes" 'BEGIN {print b/1048576}')"

      while IFS=$'\t' read -r id size name created; do
        [[ -z "${id:-}" ]] && continue
        printf '%s\t%s\t%s\t%s\t%s\n' "$repo" "$id" "$size" "$name" "$created" >> "$TMP_FILE"
      done < "$REPO_FILE"

      TOTAL_COUNT=$((TOTAL_COUNT + count))
      TOTAL_BYTES=$((TOTAL_BYTES + bytes))
    fi
  else
    echo "Avertissement : impossible de lire les artefacts de $repo." >&2
  fi

  rm -f "$REPO_FILE"
done

echo
printf "Total : %d artefact(s), %.2f MiB (%.3f GiB)\n" \
  "$TOTAL_COUNT" \
  "$(awk -v b="$TOTAL_BYTES" 'BEGIN {print b/1048576}')" \
  "$(awk -v b="$TOTAL_BYTES" 'BEGIN {print b/1073741824}')"

if (( TOTAL_COUNT == 0 )); then
  echo "Aucun artefact à supprimer."
else
  echo
  echo "ATTENTION : la suppression des artefacts est irréversible."
  read -r -p 'Tape exactement SUPPRIMER pour continuer : ' CONFIRMATION

  if [[ "$CONFIRMATION" != "SUPPRIMER" ]]; then
    echo "Suppression annulée."
    exit 0
  fi

  echo
  echo "Suppression des artefacts…"

  DELETED=0
  FAILED=0

  while IFS=$'\t' read -r repo id size name created; do
    if gh api \
        --method DELETE \
        -H "X-GitHub-Api-Version: $API_VERSION" \
        "repos/$repo/actions/artifacts/$id" \
        >/dev/null 2>&1; then
      DELETED=$((DELETED + 1))
      printf "[%d/%d] Supprimé : %s — %s\n" "$DELETED" "$TOTAL_COUNT" "$repo" "$name"
    else
      FAILED=$((FAILED + 1))
      echo "Échec : $repo — artefact $id ($name)" >&2
    fi
  done < "$TMP_FILE"

  echo
  echo "$DELETED artefact(s) supprimé(s), $FAILED échec(s)."
fi

echo
echo "Configuration de la rétention à $RETENTION_DAYS jour(s)…"

RETENTION_OK=0
RETENTION_FAILED=0

for repo in "${REPOS[@]}"; do
  if gh api \
      --method PUT \
      -H "X-GitHub-Api-Version: $API_VERSION" \
      "repos/$repo/actions/permissions/artifact-and-log-retention" \
      -F "days=$RETENTION_DAYS" \
      >/dev/null 2>&1; then
    RETENTION_OK=$((RETENTION_OK + 1))
    echo "Rétention configurée : $repo"
  else
    RETENTION_FAILED=$((RETENTION_FAILED + 1))
    echo "Échec de la configuration : $repo" >&2
  fi
done

echo
echo "Terminé."
echo "Rétention configurée sur $RETENTION_OK dépôt(s), $RETENTION_FAILED échec(s)."
echo
echo "Note : GitHub peut mettre plusieurs heures à actualiser le stockage affiché."
echo "La consommation déjà cumulée pendant le cycle de facturation peut rester visible jusqu'au 1er septembre 2026."
