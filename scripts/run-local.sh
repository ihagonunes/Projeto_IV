#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

if [[ ! -f .env ]]; then
  echo "Crie .env a partir de .env.example antes de executar." >&2
  exit 1
fi

set -a
# shellcheck disable=SC1091
source .env
set +a

mvn spring-boot:run
