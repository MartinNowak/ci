import java.nio.channels.ClosedChannelException

/*******************************************************************************

    Utils

*******************************************************************************/

/**
    Standard function to be used to do a git checkout for arbitrary URL in
    current folder. Cleans the folder (using `git reset --hard` +
    `git clean -fdx`) in the process.
 **/
def clone (repo_url, git_ref = "master") {
    checkout(
        poll: false,
        scm: [
            $class: 'GitSCM',
            branches: [[name: git_ref]],
            extensions: [[$class: 'CleanBeforeCheckout']],
            userRemoteConfigs: [[url: repo_url]]
        ]
    )
}

/**
    Function to checkout upstream that has triggered current
    pipeline, for example PR branch. For PRs this will already merge
    with the base branch.

    Requires administrator approval for allow access to:

        method hudson.plugins.git.GitSCM getBranches
        method hudson.plugins.git.GitSCMBackwardCompatibility getExtensions
        method hudson.plugins.git.GitSCM getUserRemoteConfigs
 **/
def cloneTestee () {
    checkout(scm: [
        $class: 'GitSCM',
        branches: scm.branches,
        extensions: scm.extensions + [[$class: 'CleanBeforeCheckout']],
        userRemoteConfigs: scm.userRemoteConfigs
    ])
}

/** Checks out latest SemVer-compatible release tag available in specified repo,
    or master if no tag can be found.
 **/
def cloneLatestTag (repo_url) {
    def LATEST = sh(
        script: "git ls-remote --tags ${repo_url} | sed -n 's|.*refs/tags/\\(v\\?[0-9]*\\.[0-9]*\\.[0-9]*\$\\)|\\1|p' | sort --version-sort | tail -n 1",
        returnStdout: true
    ).trim()

    checkout(
        poll: false,
        scm: [
            $class: 'GitSCM',
            branches: [[name: LATEST ?: 'master']],
            extensions: [[$class: 'CleanBeforeCheckout']],
            userRemoteConfigs: [[url: repo_url]]
        ]
    )
}

/**
    Checks whether a specific branch exists at a remote git repository.
*/
def branchExists (repo_url, branch) {
    def status = sh(
        script: "git ls-remote --exit-code --heads ${repo_url} ${branch} > /dev/null",
        returnStatus: true
    )
    return status == 0
}

/**
    Utility to simplify repeating boilerplate of defining parallel steps
    over array of folders. Creates a map from @names array where each value
    is @action called with each name respectively while being wrapped in
    `dir(name)` statement.

    NB: `action` has to be a function, not a closure, otherwise name argument
    will be captured wrongly
 **/
def mapSteps (names, action) {
    def steps = [:]

    for (int i = 0; i < names.size(); ++i) {
        def name = names[i];
        steps[name] = { action(name) }
    }

    return steps
}

/**
 Retries action up to `times` if it fails due to executor preemption.
 **/
def retryOnPreemption (action, times = 1) {
    while (times--)
    {
        try {
            action()
            return
        // Jenkins looses contact to executor
        } catch (ClosedChannelException e) {
            echo "${e.toString()}"
        }
    }
    action();
}

/*******************************************************************************

    Actions

    In Groovy it is not possible to define a "static" nested function and
    defining a closure wrongly captures a context, making it break `parallel`
    in weird ways.

    Because of that, all actions to be used with `mapSteps` are to be define
    here as global functions. Each function should be named in relation to stage
    is used in and take exactly one argument - directory name

*******************************************************************************/

def getSources (name) { dir(name) {

    echo scm.repositories[0].name
    if (scm.getRepositoryByName(name)) {
        cloneTestee()
    } else {
        // Checkout matching branches of other repos, either
        // target branch for PRs or identical branch name.
        def base_branch = env.CHANGE_TARGET ?: env.BRANCH_NAME
        def repo_url = "https://github.com/dlang/${name}.git"
        if (!branchExists(repo_url, base_branch)) {
            base_branch = 'master'
        }
        clone(repo_url, base_branch)
    }
}}

def test_travis_yaml () {
    def script = 'dub test --compiler=$DC'
    if (fileExists('.travis.yml')) {
        def travis_script = sh(script: 'get_travis_test_script', returnStdout: true).trim()
        if (travis_script)
            script = travis_script
    }
    sh script
}

def testDownstreamProject (name) {
    def repo = name // to fix issues with closure
    node { ws(dir: 'dlang_projects') {
        unstash name: "dlang-build"
        withEnv([
                    // KEY+UID prepends to EnvVars, see http://javadoc.jenkins.io/hudson/EnvVars.html
                    "PATH+BIN=${env.WORKSPACE}/distribution/bin",
                    "LIBRARY_PATH+LIB=${env.WORKSPACE}/distribution/libs",
                    "LD_LIBRARY_PATH+LIB=${env.WORKSPACE}/distribution/libs",
                    'DC=dmd',
                    'DMD=dmd',
                    // set HOME to separate concurrent ~/.dub user paths
                    "HOME=${env.WORKSPACE}"
                ]) {
            try { dir(repo) {

                if (repo == 'rejectedsoftware/vibe.d') {
                    clone("https://github.com/${repo}.git", 'v0.8.3-alpha.1')
                } else if (repo == "sociomantic-tsunami/ocean") {
                    clone("https://github.com/${repo}.git", 'v4.0.0-alpha.5')
                } else {
                    cloneLatestTag("https://github.com/${repo}.git")
                }

                switch (repo) {
                case 'gtkd-developers/GtkD':
                    sh 'make DC=$DC'
                    break;

                case 'higgsjs/Higgs':
                    sh 'make -C source test DC=$DC'
                    break;

                case 'rejectedsoftware/vibe.d':
                    // use DC=dmd to workaround https://github.com/dlang/dub/pull/966
                    sh 'sed -i \'/# test building with Meson/,//d\' travis-ci.sh' // strip meson tests
                    // temporarily disable failing tests, see: https://github.com/dlang/ci/pull/96
                    sh 'rm -rf tests/vibe.core.net.1726' // FIXME
                    sh 'rm -rf tests/std.concurrency' // FIXME

                    sh 'DC=dmd VIBED_DRIVER=libevent BUILD_EXAMPLE=1 RUN_TEST=1 ./travis-ci.sh'
                    sh 'DC=dmd VIBED_DRIVER=libasync BUILD_EXAMPLE=0 RUN_TEST=0 ./travis-ci.sh || echo failed' // FIXME
                    break;

                case 'rejectedsoftware/diet-ng':
                    sh 'sed -i \'/mkdir build && cd build/,//d\' .travis.yml' // strip meson tests
                    test_travis_yaml()
                    break;

                case 'dlang/dub':
                    sh '''
                      rm test/issue884-init-defer-file-creation.sh # FIXME
                      sed -i \'/^source.*activate/d\' travis-ci.sh
                    '''
                    sh 'DC=$DC ./travis-ci.sh'
                    break;

                case 'msgpack/msgpack-d':
                    sh 'DMD=$DMD MODEL=64 make -f posix.mak unittest'
                    break;

                case 'economicmodeling/containers':
                    sh 'make -B -C test/ || echo failed' // FIXME
                    break;

                case 'BlackEdder/ggplotd':
                    // workaround https://github.com/BlackEdder/ggplotd/issues/34
                    sh 'sed -i \'s|auto seed = unpredictableSeed|auto seed = 54321|\' source/ggplotd/example.d'
                    test_travis_yaml()
                    break;

                case 'dlang-community/D-YAML':
                    sh 'dub build --compiler=$DC'
                    sh 'dub test --compiler=$DC'
                    break;

                case 'sociomantic-tsunami/ocean':
                    sh '''
                    git submodule update --init
                    make d2conv V=1
                    make test V=1 DVER=2 F=production ALLOW_DEPRECATIONS=1
                    '''
                    break;

                case 'eBay/tsv-utils-dlang':
                    sh 'make test DCOMPILER=$DC'
                    break;

                default:
                    test_travis_yaml()
                    break;
                }
            }}
            finally {
                sh """
                dub clean --all-packages >/dev/null
                # workaround https://github.com/dlang/dub/issues/1256
                if [ -d '${env.WORKSPACE}/.dub/packages' ]; then
                    find '${env.WORKSPACE}/.dub/packages' -type f -name '*.a' -delete
                fi
                git -C '${repo}' clean -dxf >/dev/null
                rm -r '${env.WORKSPACE}/distribution'
                """
            }
        }
    }}
}

def buildDlang() {
    /* Use the same workspace, no matter what job (dmd, druntime,...)  triggered
     * the build.  The workspace step will take care of concurrent test-runs and
     * allocate additional workspaces if necessary.  This setup avoids to
     * reclone repos for each test-run.
     */
    node { ws(dir: 'dlang_ci') {
        stage ('Toolchain and System information') {
            sh '''#!/usr/bin/env bash
            set -xueo pipefail

            uname -a
            make --version
            ${SHELL} --version || true
            c++ --version
            ld -v
            ! command -v gdb &>/dev/null || gdb --version
            '''
        }

        def projects = [ 'dmd', 'druntime', 'phobos', 'dub', 'tools' ]

        stage ('Clone') {
            parallel mapSteps(projects, this.&getSources)
        }

        stage ('Build Compiler') {
            // main compilation process can't be parallel because each repo
            // expects previous one to be already built and present in parent
            // folder

            def action = { sh "make -f posix.mak AUTO_BOOTSTRAP=1 --jobs=4" }

            dir('dmd',      action)
            dir('druntime', action)
            dir('phobos',   action)
        }

        stage ('Build Tools') {
            def repos = [
                'dub': {
                    withEnv(["PATH=${env.WORKSPACE}/dmd/generated/linux/release/64:${env.PATH}"]) {
                        dir ('dub') { sh "./build.sh" }
                    }
                },
                'tools': {
                    withEnv(["PATH=${env.WORKSPACE}/dmd/generated/linux/release/64:${env.PATH}"]) {
                        dir ('tools') { sh "make -f posix.mak RELEASE=1 --jobs=4" }
                    }
                }
            ]

            parallel repos
        }

        stage ("Package distribution") {
            // ideally this step should be in sync with the release tars
            sh '''#!/usr/bin/env bash
            set -ueo pipefail

            rm -rf distribution
            mkdir -p distribution/{bin,imports,libs}
            cp --archive --link dmd/generated/linux/release/64/dmd dub/bin/dub tools/generated/linux/64/rdmd distribution/bin/
            cp --archive --link phobos/etc phobos/std druntime/import/* distribution/imports/
            cp --archive --link phobos/generated/linux/release/64/libphobos2.{a,so,so*[!o]} distribution/libs/
            echo '[Environment]
DFLAGS=-I%@P%/../imports -L-L%@P%/../libs -L--export-dynamic -L--export-dynamic -fPIC' > distribution/bin/dmd.conf
            '''
            stash name: "dlang-build", includes: "distribution/**"

            sh 'rm -r distribution'
            projects.each { p -> dir(p, { sh 'git clean -dxf >/dev/null' }) }
        }
    }}
}

/*******************************************************************************

    Stages

*******************************************************************************/

def call() { timeout(time: 1, unit: 'HOURS') {
    // https://github.com/MartinNowak/jenkins-cancel-build-on-update
    cancelPreviousBuild()

    retryOnPreemption({ buildDlang(); });

    def dub_projects = [
        // sorted by test time slow to fast
        "rejectedsoftware/vibe.d",
        "dlang/dub",
        "sociomantic-tsunami/ocean",
        "higgsjs/Higgs", // 3m10s
        "BlackEdder/ggplotd", // 1m56s
        "eBay/tsv-utils-dlang", // 1m41s
        "dlang-community/D-Scanner", // 1m40s
        "rejectedsoftware/diet-ng", // 56s
        "atilaneves/unit-threaded", //36s
        "d-gamedev-team/gfm", // 28s
        "dlang-community/DCD", // 23s
        "CyberShadow/ae", // 22s
        "dlang-community/D-YAML", // 15s
        "dlang-community/libdparse", // 13s
        "dlang-community/dfmt", // 11s
        // run in under 10s, sorted alphabetically
        "Abscissa/libInputVisitor",
        "DerelictOrg/DerelictFT",
        "DerelictOrg/DerelictGL3",
        "DerelictOrg/DerelictGLFW3",
        "DerelictOrg/DerelictSDL2",
        "dlang/undeaD",
        "DlangScience/scid",
        "Netflix/vectorflow",
        "ariovistus/pyd",
        "economicmodeling/containers",
        "kyllingstad/zmqd",
        "lgvz/imageformats",
        "msgpack/msgpack-d",
        "msoucy/dproto",
        "nomad-software/dunit",
        "PhilippeSigaud/Pegged",
        "repeatedly/mustache-d",
        "s-ludwig/taggedalgebraic",
    ]

    stage ('Test Projects') {
        parallel mapSteps(dub_projects, { p -> retryOnPreemption({ testDownstreamProject(p); }); })
    }
}}

return this; // return script
