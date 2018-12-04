#!/bin/bash

PS4="~> " # needed to avoid accidentally generating collapsed output
set -uexo pipefail

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
if [ $# -ge 1 ]; then
    repositories=($@)
else
    repositories=(dmd druntime phobos tools dub)
fi

for dir in "${repositories[@]}" ; do
    repo="https://github.com/dlang/$dir.git"
    branch=$("$DIR/origin_target_branch.sh" "$repo")
    echo "target_branch: $branch"
    git clone -b "$branch" --depth 1 "$repo" "dlang/$dir"
    if [ "$repo" = "${BUILDKITE_REPO:-}" ] && [ -n "${BUILDKITE_PULL_REQUEST:-}" ]; then
        git -C "dlang/$dir" fetch origin "refs/pull/${BUILDKITE_PULL_REQUEST}/merge"
        git -C "dlang/$dir" checkout -f FETCH_HEAD
    fi
done
