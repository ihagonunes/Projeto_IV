#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

env_file="${1:-.env}"
if [[ ! -f "$env_file" ]]; then
  echo "Arquivo de ambiente não encontrado: $env_file" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1091
source "$env_file"
set +a

mvn spring-boot:run
