#!/bin/bash

error() {
    echo >&2 "* Error: $@"
}

fatal() {
    error "$@"
    exit 1
}

message() {
    echo "$@"
}

update-git-repo() {
    # $1   - git repository url
    # $2   - source dir, where to clone
    # rest - additional arguments:
    #
    #        -n                     - don't chekout working dir        (git clone -n)
    #        --pull                 - pull instead of fetching         (git pull --rebase)
    #        --reset                - hard reset before pulling        (git reset --hard HEAD)
    #        --xrepo|--Xrepo <repo> - override the default repository
    #                                 with <repo>
    #        -b <branch>            - Point the local HEAD to <branch> (git clone -b <branch>
    #                                                                   git checkout <branch>)

    local git_url="${1:?}"
    local source_dir="${2:?}"
    shift 2

    local git_cmd=fetch
    local git_cmd_opt=
    local git_clone_opt=
    local git_branch=
    local git_reset=false
    while [ $# -gt 0 ]; do
        case "$1" in
            --pull)
                git_cmd=pull
                git_cmd_opt=--rebase
                ;;
            --reset)
                git_reset=true
                ;;
            -n)
                git_clone_opt="$git_clone_opt -n"
                ;;
            --xrepo|--Xrepo)
                shift
                if [ $# -gt 0 ]; then
                    if [ "${git_url}" ]; then
                        message "Overriding git URL \"${git_url:?}\" with \"${1:?}\""
                    fi
                    git_url="${1:?}"
                else
                    error "Missing argument (git URL) to --xrepo|--Xrepo option"
                    return 1
                fi
                ;;
            -b)
                shift
                if [ $# -gt 0 ]; then
                    if [ "${git_branch}" ]; then
                        message "Overriding git branch \"${git_branch:?}\" with \"${1:?}\""
                    fi
                    git_branch="${1:?}";
                    git_clone_opt="$git_clone_opt -b ${git_branch:?}"
                else
                    error "Missing argument (remote branch name) to -b option"
                    return 1
                fi
                ;;
            '')
                # Ignore empty argument
                shift;;
            *) eval $(devenv_die "updateGitRepo: Unrecognized argument: \"$1\"");
               ;;
        esac
        shift
    done

    local exit_code

    if [ "${git_branch}" ]; then
        message "Selecting ${git_branch:?} branch as HEAD"
    fi

    if [ -e "$source_dir" ]; then
        cd "$source_dir"

        if [ "$git_cmd" = "pull" -a "$git_reset" = "true" ]; then
            git reset --hard HEAD
        fi

        if [ "${git_branch}" ]; then
            git fetch && git checkout "${git_branch:?}"
            exit_code=$?
            if [ $exit_code -eq 0 ]; then
                if [ "$git_cmd" = "pull" ] && ! git symbolic-ref -q HEAD > /dev/null; then
                    # HEAD is detached, git pull will not work
                    # FIXME: Following is not really nice and should be improved
                    git fetch && git fetch --tags && git checkout "${git_branch:?}"
                else
                    git "$git_cmd" $git_cmd_opt
                fi
                exit_code=$?
            fi
        else
            git "$git_cmd" $git_cmd_opt
            exit_code=$?
        fi
        cd - &> /dev/null
        # simple error check for incomplete git clone
        if [ $exit_code -ne 0 -a ! -e "$source_dir/.git" ]; then
            error "'git $git_cmd $git_cmd_opt' failed !"
            error "Directory $source_dir exists but $source_dir/.git directory missing."
            error "Possibly 'git clone' command failed."
            error "You can try to remove $source_dir directory and try to update again."
        fi
    else
        message "Cloning $git_url to $source_dir..."
        test "${git_branch}" &&
            message "Selecting ${git_branch:?} branch as HEAD"
        git clone "$git_url" "$source_dir" $git_clone_opt
        exit_code=$?
    fi
    return $exit_code
}

THIS_DIR=$(dirname "$(readlink -f "$BASH_SOURCE")")

if ! type -f git 2> /dev/null; then
    fatal "No git tool detected"
fi

############### Configuration ###############

APP_NAME=serendipity

JAVA_MAVEN_DEPS=(
    https://github.com/rmrschub/igraphstore
)

IMAGE_TAG=serendipity

############# End Configuration #############

usage() {
    echo "Build $APP_NAME application"
    echo
    echo "$0 [options]"
    echo "options:"
    echo "      --java-deps=           Java Maven project dependencies in"
    echo "                             build order"
    echo "                             (default ${JAVA_MAVEN_DEPS[@]})"
    echo "  -t, --tag=                 Image tag"
    echo "                             (default: $IMAGE_TAG)"
    echo "      --no-cache             Disable Docker cache"
    echo "      --help                 Display this help and exit"
}

while [[ $# > 0 ]]; do
    case "$1" in
        --java-deps)
            JAVA_MAVEN_DEPS=($2)
            shift 2
            ;;
        --java-deps=*)
            JAVA_MAVEN_DEPS=(${1#*=})
            shift
            ;;
        -t|--tag)
            IMAGE_TAG="$2"
            shift 2
            ;;
        --tag=*)
            IMAGE_TAG="${1#*=}"
            shift
            ;;
        --no-cache)
            NO_CACHE=--no-cache
            shift
            ;;
        --help)
            usage
            exit
            ;;
        --)
            shift
            break
            ;;
        -*)
            fatal "Unknown option $1"
            ;;
        *)
            break
            ;;
    esac
done

echo "$APP_NAME Image Configuration:"
echo "JAVA_MAVEN_DEPS:   ${JAVA_MAVEN_DEPS[@]}"
echo "IMAGE_TAG:         $IMAGE_TAG"
echo "NO_CACHE:          $NO_CACHE"

[ ! -d "$THIS_DIR/deps" ] && mkdir -p "$THIS_DIR/deps"

for ((i = 0; i < ${#JAVA_MAVEN_DEPS[@]}; i++)); do
    repo="${JAVA_MAVEN_DEPS[i]}"
    dir_name=$(basename "$repo" .git)
    dir="$THIS_DIR/deps/$(printf '%05d' $i)"
    update-git-repo "$repo" "$dir" --reset --pull || \
        fatal "Could not update $repo repository"
done

docker build \
       --build-arg=APP_NAME=$APP_NAME \
       -t "$IMAGE_TAG" \
       "$THIS_DIR" && \
    echo "Successfully built docker image $IMAGE_TAG"
