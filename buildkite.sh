#!/bin/bash

read -r -d '' LOAD_DISTRIBUTION <<- EOM
        echo "--- Load distribution archive"
        buildkite-agent artifact download distribution.tar.xz .
        tar xfJ distribution.tar.xz
        rm -rf buildkite
        mv distribution/buildkite buildkite
        rm distribution.tar.xz
        source ./buildkite/load_distribution.sh
EOM

read -r -d '' DEFAULT_COMMAND_PROPS <<- EOM
    branches: !dmd-cxx
    timeout_in_minutes: 40
    retry:
      automatic:
        limit: 2
EOM

cat << EOF
steps:
  - command: |
        echo "--- Print environment"
        uname -a
        make --version
        \\\${SHELL} --version || true
        c++ --version
        ld -v
        ! command -v gdb &>/dev/null || gdb --version
        ! dmd --version # ensure that no dmd is the current environment
        # just to be sure there isn't anything old left
        rm -rf .
        echo "--- Cloning dlang core repos"
        ./buildkite/clone_dlang.sh
        echo "--- Building distribution"
        ./buildkite/build_distribution.sh
    label: "Build"
    artifact_paths: "distribution.tar.xz"
    ${DEFAULT_COMMAND_PROPS}
EOF

################################################################################

cat << 'EOF'
  - wait
EOF

################################################################################
# Style & coverage targets
# Must run after the 'wait' to avoid blocking the build_distribution step
# (and thus all subsequent project builds)
################################################################################

case "${BUILDKITE_REPO:-x}" in
    "https://github.com/dlang/dmd.git" | \
    "https://github.com/dlang/druntime.git" | \
    "https://github.com/dlang/phobos.git")

cat << EOF
  - command: |
        ${LOAD_DISTRIBUTION}
        echo "--- Cloning dlang core repos"
        ./buildkite/clone_dlang.sh
        echo "--- Running style testing"
        ./buildkite/style.sh
    label: "Style"
    ${DEFAULT_COMMAND_PROPS}

  - command: |
        ${LOAD_DISTRIBUTION}
        echo "--- Cloning dlang core repos"
        ./buildkite/clone_dlang.sh
        echo "--- Running coverage testing"
        ./buildkite/test_coverage.sh
    label: "Coverage"
    ${DEFAULT_COMMAND_PROPS}
EOF
        ;;
    *)
        ;;
esac

################################################################################
# Add your project here.
# By default, the Project Tester will perform your Travis 'script' tests.
# If a different action is preferred, set it in buildkite/build_project.sh
################################################################################
projects=(
    # sorted by test time fast to slow (to minimize pending queue length)
    "vibe-d/vibe.d+libevent-examples" # 12m1s
    "vibe-d/vibe.d+vibe-core-examples" # 9m51s
    "vibe-d/vibe.d+libevent-tests" # 8m35s
    "vibe-d/vibe.d+vibe-core-tests" # 6m44s
    "ldc-developers/ldc" # 4m49s
    "vibe-d/vibe.d+libevent-base" # 4m20s
    "vibe-d/vibe.d+vibe-core-base" # 4m31s
    # https://github.com/vibe-d/vibe.d/issues/2157
    "vibe-d/vibe.d+libasync-base" # 3m45s
    "dlang/phobos" # 4m50s
    "sociomantic-tsunami/ocean" # 4m49s
    "dlang/dub" # 3m55s
    "vibe-d/vibe-core+epoll" # 3m38s
    "vibe-d/vibe-core+select" # 3m30s
    "higgsjs/Higgs" # 3m10s
    "rejectedsoftware/ddox" # 2m42s
    "BlackEdder/ggplotd" # 1m56s
    "d-language-server/dls" # 1m55s
    "eBay/tsv-utils" # 1m41s
    "dlang-community/D-Scanner" # 1m40s
    "dlang/druntime" # 1m18s
    "dlang-tour/core" # 1m17s
    "d-widget-toolkit/dwt" # 1m16s
    "rejectedsoftware/diet-ng" # 56s
    "mbierlee/poodinis" # 40s
    "dlang/tools" # 40s
    #"atilaneves/unit-threaded" #36s
    "d-gamedev-team/gfm" # 28s
    "gecko0307/dagon" # 25s
    "dlang-community/DCD" # 23s
    "weka-io/mecca" # 22s
    "CyberShadow/ae" # 22s
    "jmdavis/dxml" # 22s
    "libmir/mir-algorithm" # 17s
    "dlang-community/D-YAML" # 15s
    "libmir/mir-random" # 13s
    "dlang-community/libdparse" # 13s
    "BBasile/iz" # 12s
    "aliak00/optional" # 12s
    "dlang-community/dfmt" # 11s
    # run in under 10s sorted alphabetically
    "Abscissa/libInputVisitor"
    #"ariovistus/pyd"
    "atilaneves/automem"
    "AuburnSounds/intel-intrinsics"
    "DerelictOrg/DerelictFT"
    "DerelictOrg/DerelictGL3"
    "DerelictOrg/DerelictGLFW3"
    "DerelictOrg/DerelictSDL2"
    "dlang-bots/dlang-bot"
    "dlang-community/containers"
    "dlang/undeaD"
    "DlangScience/scid"
    "ikod/dlang-requests"
    "kaleidicassociates/excel-d"
    "kaleidicassociates/lubeck"
    "kyllingstad/zmqd"
    "lgvz/imageformats"
    "libmir/mir"
    "msoucy/dproto"
    "Netflix/vectorflow"
    "nomad-software/dunit"
    "pbackus/sumtype"
    "PhilippeSigaud/Pegged"
    "repeatedly/mustache-d"
    "s-ludwig/std_data_json"
    "s-ludwig/taggedalgebraic"
)
# Add all projects that require more than 3GB of memory to build
declare -A memory_req
memory_req["BlackEdder/ggplotd"]=high
memory_req["BBasile/iz"]=high
memory_req["dlang-community/D-Scanner"]=high
memory_req["vibe-d/vibe-core+select"]=high
memory_req["vibe-d/vibe-core+epoll"]=high
memory_req["vibe-d/vibe.d+vibe-core-base"]=high
memory_req["vibe-d/vibe.d+libevent-base"]=high
memory_req["vibe-d/vibe.d+libasync-base"]=high
memory_req["libmir/mir-algorithm"]=high
memory_req["sociomantic-tsunami/ocean"]=high
memory_req["dlang-bots/dlang-bot"]=high
memory_req["dlang/phobos"]=high
memory_req["dlang/dub"]=high
memory_req["higgsjs/Higgs"]=high
memory_req["d-language-server/dls"]=high

# self-test PRs to dlang/ci
projects+=("dlang/ci")

for project_name in "${projects[@]}" ; do
    project="$(echo "$project_name" | sed "s/\([^+]*\)+.*/\1/")"
cat << EOF
  - command: |
        # just to be sure there isn't anything old left
        rm -rf .

        export REPO_URL="https://github.com/${project}"
        export REPO_FULL_NAME="${project_name}"

        ${LOAD_DISTRIBUTION}
        ./buildkite/build_project.sh
    label: "${project_name}"
    ${DEFAULT_COMMAND_PROPS}
EOF

if [ "${memory_req["$project_name"]:-x}" != "x" ] ; then
cat << EOF
    agents:
      - "memory=${memory_req["$project_name"]}"
EOF
fi

done
