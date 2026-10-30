#!/bin/sh
set -eu
mkdir -p "${MC_CONFIG_DIR}"
mc alias set local http://minio:9000 "${MINIO_ROOT_USER}" "${MINIO_ROOT_PASSWORD}"
mc mb --ignore-existing local/documents
mc ls local/documents
