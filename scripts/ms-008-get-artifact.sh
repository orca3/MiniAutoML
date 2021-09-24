#!/usr/bin/env bash
source "$(dirname "$0")/env-vars.sh"

echo
if [ "$1" != "" ] && [ "$2" != "" ]; then
  name="${1}"
  version="${2}"
  echo "GetArtifact $name version $version"
  grpcurl -plaintext \
    -d "{
    \"name\":\"$name\",
    \"version\":\"$version\"
  }" \
    localhost:"${MS_PORT}" metadata_store.MetadataStoreService/GetArtifact
else
    echo "Requires artifact_name as the first parameter"
    echo "Requires artifact_version as the second parameter"

fi
