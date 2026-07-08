#!/usr/bin/env bash
#
# Copyright 2026 BarqDB
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
# implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -euo pipefail

release_dir="${1:?Usage: upload-maven-central.sh <local-maven-repo> [deployment-name]}"
deployment_name="${2:-barq-kotlin-${GITHUB_REF_NAME:-manual}}"

: "${MAVEN_CENTRAL_USERNAME:?MAVEN_CENTRAL_USERNAME is required}"
: "${MAVEN_CENTRAL_PASSWORD:?MAVEN_CENTRAL_PASSWORD is required}"

if [[ ! -d "$release_dir" ]]; then
    echo "Release directory does not exist: $release_dir" >&2
    exit 1
fi

bundle="$(mktemp -t barq-kotlin-maven-central.XXXXXX).zip"
auth_token="$(printf "%s:%s" "$MAVEN_CENTRAL_USERNAME" "$MAVEN_CENTRAL_PASSWORD" | base64 | tr -d '\n')"
echo "::add-mask::$auth_token"

find "$release_dir" -name "maven-metadata-local.xml*" -delete

(
    cd "$release_dir"
    zip -qry "$bundle" .
)

upload_url="https://central.sonatype.com/api/v1/publisher/upload?name=${deployment_name}&publishingType=AUTOMATIC"
status_url="https://central.sonatype.com/api/v1/publisher/status"

deployment_id="$(
    curl -fsS \
        -H "Authorization: Bearer ${auth_token}" \
        -F "bundle=@${bundle};type=application/octet-stream" \
        "$upload_url" |
        tr -d '"[:space:]'
)"

if [[ -z "$deployment_id" ]]; then
    echo "Central Portal did not return a deployment id." >&2
    exit 1
fi

echo "Central Portal deployment id: $deployment_id"

for _ in $(seq 1 120); do
    status_json="$(
        curl -fsS \
            -X POST \
            -H "Authorization: Bearer ${auth_token}" \
            "${status_url}?id=${deployment_id}"
    )"
    state="$(python3 -c 'import json, sys; print(json.load(sys.stdin).get("deploymentState", ""))' <<< "$status_json")"
    echo "Central Portal deployment state: $state"

    case "$state" in
        PUBLISHED)
            exit 0
            ;;
        FAILED)
            echo "$status_json" >&2
            exit 1
            ;;
    esac

    sleep 30
done

echo "Timed out waiting for Central Portal deployment to publish." >&2
exit 1
