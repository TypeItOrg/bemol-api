#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.."

if [[ $# -ne 0 ]]; then
    echo "Uso: $0" >&2; exit 2
fi

: "${SEED_INSTITUTION:?Indicar SEED_INSTITUTION con el código de la institución destino}"
SEED_YEAR=${SEED_YEAR:-$(date +%Y)}
# Preserve the identity namespace used by existing demo records.
seed_dataset=boero-2025
SEED_TIMEZONE=${SEED_TIMEZONE:-America/Argentina/Cordoba}
SEED_TRANSPORT=${SEED_TRANSPORT:-compose}
if [[ ! "$SEED_YEAR" =~ ^[0-9]{1,4}$ ]]; then
    echo 'SEED_YEAR debe ser un año de 1 a 9998.' >&2; exit 2
fi

# Assemble completely before connecting: a missing file must not commit a partial load.
sql=$(mktemp)
trap 'rm -f "$sql"' EXIT
cat src/main/resources/db/seed/enrollment-demo-context.sql \
    src/main/resources/db/seed/enrollment-demo-ids.sql \
    src/main/resources/db/seed/datasets/catalog.sql \
    src/main/resources/db/seed/datasets/scenario.sql \
    src/main/resources/db/seed/enrollment-demo-validate.sql \
    src/main/resources/db/seed/enrollment-demo.sql > "$sql"
args=(-X -v ON_ERROR_STOP=1
    -v "seed_institution=$SEED_INSTITUTION" -v "seed_year=$SEED_YEAR"
    -v "seed_dataset=$seed_dataset" -v "seed_reference_date=${SEED_REFERENCE_DATE:-}"
    -v "seed_timezone=$SEED_TIMEZONE")
case "$SEED_TRANSPORT" in
    compose)
        compose=(docker compose)
        if [[ -n "${SEED_COMPOSE_FILE:-}" ]]; then compose+=(-f "$SEED_COMPOSE_FILE"); fi
        "${compose[@]}" exec -T "${SEED_DB_SERVICE:-postgres}" sh -c \
            'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" "$@"' sh "${args[@]}" < "$sql"
        ;;
    psql)
        # Standard libpq connection variables: PGHOST, PGPORT, PGDATABASE, PGUSER, PGPASSWORD/PGPASSFILE.
        psql "${args[@]}" < "$sql"
        ;;
    *) echo 'SEED_TRANSPORT debe ser compose o psql.' >&2; exit 2 ;;
esac
