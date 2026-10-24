#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.."

if [[ $# -ne 0 ]]; then
    echo "Uso: $0" >&2; exit 2
fi

: "${SEED_INSTITUTION:?Indicar SEED_INSTITUTION con el UUID de la institución destino}"
SEED_YEAR=${SEED_YEAR:-$(date +%Y)}
# Keep one stable dataset identity across reruns.
seed_dataset=bemol-2025
SEED_TIMEZONE=${SEED_TIMEZONE:-America/Argentina/Cordoba}
SEED_TRANSPORT=${SEED_TRANSPORT:-compose}
if [[ ! "$SEED_YEAR" =~ ^[0-9]{1,4}$ ]]; then
    echo 'SEED_YEAR debe ser un año de 1 a 9998.' >&2; exit 2
fi
if [[ ! "$SEED_INSTITUTION" =~ ^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$ ]]; then
    echo 'SEED_INSTITUTION debe ser un UUID válido.' >&2; exit 2
fi
asset=src/main/resources/db/seed/assets/presentacion-musical.pdf
asset_prefix="seed-demo/$SEED_INSTITUTION/$SEED_YEAR"

# Assemble completely before connecting: a missing file must not commit a partial load.
sql=$(mktemp)
trap 'rm -f "$sql"' EXIT
cat src/main/resources/db/seed/enrollment-demo-context.sql \
    src/main/resources/db/seed/enrollment-demo-ids.sql \
    src/main/resources/db/seed/datasets/catalog.sql \
    src/main/resources/db/seed/datasets/scenario.sql \
    src/main/resources/db/seed/enrollment-demo-validate.sql \
    src/main/resources/db/seed/enrollment-demo.sql \
    src/main/resources/db/seed/enrollment-demo-dependents.sql > "$sql"
printf '\nCOMMIT;\n' >> "$sql"
args=(-X -v ON_ERROR_STOP=1
    -v "seed_institution=$SEED_INSTITUTION" -v "seed_year=$SEED_YEAR"
    -v "seed_dataset=$seed_dataset" -v "seed_reference_date=${SEED_REFERENCE_DATE:-}"
    -v "seed_timezone=$SEED_TIMEZONE"
    -v "seed_asset_prefix=$asset_prefix" -v "seed_asset_size=$(wc -c < "$asset")")
compose=(docker compose)
if [[ -n "${SEED_COMPOSE_FILE:-}" ]]; then compose+=(-f "$SEED_COMPOSE_FILE"); fi

# Copy real files before inserting attachment metadata. Never replace an existing file.
copy_asset='set -eu
    test "${STORAGE_PROVIDER:-local}" = local || { echo "El seed requiere almacenamiento local." >&2; exit 1; }
    target="${STORAGE_LOCAL_DIR:?Indicar el directorio de almacenamiento}/$1"
    mkdir -p "$(dirname "$target")"
    temporary=$(mktemp "${target}.XXXXXX")
    trap '\''rm -f "$temporary"'\'' EXIT
    cat > "$temporary"
    if test -e "$target"; then
        cmp -s "$temporary" "$target" || { echo "El adjunto existente es diferente; no se sobrescribe." >&2; exit 1; }
    else
        chmod 644 "$temporary"
        ln "$temporary" "$target"
    fi'
for member in 1 3; do
    key="$asset_prefix/application-$member.pdf"
    case "$SEED_TRANSPORT" in
        compose) "${compose[@]}" exec -T "${SEED_API_SERVICE:-dev}" sh -c "$copy_asset" sh "$key" < "$asset" ;;
        psql) STORAGE_LOCAL_DIR="${SEED_STORAGE_DIR:?Indicar SEED_STORAGE_DIR para los adjuntos}" sh -c "$copy_asset" sh "$key" < "$asset" ;;
        *) echo 'SEED_TRANSPORT debe ser compose o psql.' >&2; exit 2 ;;
    esac
done
case "$SEED_TRANSPORT" in
    compose)
        # Shared infra can retain a different initialization database in POSTGRES_DB.
        "${compose[@]}" exec -T "${SEED_DB_SERVICE:-postgres}" sh -c \
            'exec psql -U "$POSTGRES_USER" -d "${DB_NAME:-$POSTGRES_DB}" "$@"' sh "${args[@]}" < "$sql"
        ;;
    psql)
        # Standard libpq connection variables: PGHOST, PGPORT, PGDATABASE, PGUSER, PGPASSWORD/PGPASSFILE.
        psql "${args[@]}" < "$sql"
        ;;
    *) echo 'SEED_TRANSPORT debe ser compose o psql.' >&2; exit 2 ;;
esac
echo 'Carga completada. Los registros existentes no se sobrescribieron.'
