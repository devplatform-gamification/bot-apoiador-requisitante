#!/bin/bash

if [ ${#} -ne 4 ]; then
    echo "Nao foram passados os 4 argumentos."
    echo "Utilize: [RANCHER_AUTHTOKEN] [RANCHER_URL] [RANCHER_PROJECTID] [RANCHER_SERVICEID]"
    exit 1;
fi;

RANCHER_AUTHTOKEN=${1}
RANCHER_URL=${2}
RANCHER_PROJECTID=${3}
RANCHER_SERVICEID=${4}

now="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"

curl -H "Authorization:Bearer ${RANCHER_AUTHTOKEN}" -H 'Content-type:application/json' -X PUT "${RANCHER_URL}/project/${RANCHER_PROJECTID}/workloads/deployment:${RANCHER_SERVICEID}"  --data-binary '{"annotations":{"cattle.io/timestamp":"'"${now}"'"  }}' --compressed  2> /dev/null 1> /dev/null
