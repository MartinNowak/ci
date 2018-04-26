#!/usr/bin/env bash

set -ueo pipefail
cd "$(dirname "$0")"

TMP=~/.ansible/tmp/hcloud.cache
if ! [ -f "$TMP" ] || [ $(( $(date '+%s') - $(stat -c %Y "$TMP") )) -gt 3600 ]; then
    HCLOUD_TOKEN=$(pass dlangci/hcloud) ../library/hcloud_inventory > "$TMP"
fi
cat "$TMP"
