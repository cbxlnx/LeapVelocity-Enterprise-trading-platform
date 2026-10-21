#!/usr/bin/env sh
set -eu
task_root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
task_project="leap-risk-smoke-$$"
task_compose="$task_root/scripts/risk-validation/docker-compose.smoke.yml"
cleanup() {
    docker compose -p "$task_project" -f "$task_compose" down --volumes --remove-orphans >/dev/null
}
trap cleanup EXIT HUP INT TERM
docker compose -p "$task_project" -f "$task_compose" up --build --abort-on-container-exit --exit-code-from java-tests
