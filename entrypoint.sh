#!/bin/bash

env >&2

message() {
    echo >&2 "[entrypoint.sh] $*"
}

info() {
    message "info: $*"
}

error() {
    echo >&2 "* [entrypoint.sh] Error: $*"
}

fatal() {
    error "$@"
    exit 1
}

message "info: EUID=$EUID args: $*"

# Checks

if [[ -z "$WAR_FILE_PATH" ]]; then
    fatal "WAR_FILE_PATH environment variable is not set"
fi

if [[ ! -r "$WAR_FILE_PATH" ]]; then
    fatal "WAR file $WAR_FILE_PATH does not exist or is not readable"
fi

if [[ -z "$CATALINA_BASE" && -z "$CATALINA_HOME" ]]; then
    fatal "CATALINA_HOME and CATALINA_BASE environment variable are not set"
fi

if [[ -z "$CATALINA_BASE" ]]; then
    CATALINA_BASE=$CATALINA_HOME
fi

CONF_DIR=$CATALINA_BASE/conf

if [[ ! -d "$CONF_DIR" ]]; then
    fatal "Configuration directory $CONF_DIR does not exist"
fi

if [[ ! -w "$CONF_DIR/server.xml" ]]; then
    fatal "File $CONF_DIR/server.xml does not exist or is not writable"
fi

# Parse command line

if [[ -z "$CONTEXT_PATH" ]]; then
    CONTEXT_PATH=${WAR_FILE_PATH%.war}
    CONTEXT_PATH=/${CONTEXT_PATH##*/}
fi

if [[ -z "$PORT" ]]; then
    PORT=8080
fi

ENTRYPOINT_CONFIG=
CATALINA_CONFIG=()
KEEP_WEBAPPS=false

usage() {
    echo "Tomcat Entrypoint Script"
    echo ""
    echo "$0 [options]"
    echo "options:"
    echo "      -D<name>=<value>       Set Java's system property"
    echo "      --context-path=        Tomcat Context path (default: '$CONTEXT_PATH')" 
    echo "      --port=                Tomcat Connector port (default '$PORT')"
    echo "      --proxy-name=          Tomcat Connector proxyName (default '$PROXY_NAME')"
    echo "      --proxy-port=          Tomcat Connector proxyPort (default '$PROXY_PORT')"
    echo "      --http-proxy-host=     Set http proxy host (default: '$HTTP_PROXY_HOST')"
    echo "      --http-proxy-port=     Set http proxy port (default: '$HTTP_PROXY_PORT')"
    echo "      --keep-webapps         Keep webapps"
    echo "                             (by default all pre-installed webapps are removed)"
    echo "      --catalina-config=     Add configuration option to $CATALINA_BASE/bin/setenv.sh"
    echo "      --entrypoint-config=   Load entrypoint configuration from"
    echo "                             specified file"
    echo "      --help"
    echo "      --help-entrypoint      Display this help and exit"
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        -D*)
            JAVA_OPTS="$JAVA_OPTS $1"
            shift
            ;;
        --context-path)
            CONTEXT_PATH="$2"
            shift 2 || fatal "Missing argument to $1"
            ;;
        --context-path=*)
            CONTEXT_PATH="${1#*=}"
            shift
            ;;
        --port)
            PORT="$2"
            shift 2 || fatal "Missing argument to $1"
            ;;
        --port=*)
            PORT="${1#*=}"
            shift
            ;;
        --proxy-name)
            PROXY_NAME="$2"
            shift 2 || fatal "Missing argument to $1"
            ;;
        --proxy-name=*)
            PROXY_NAME="${1#*=}"
            shift
            ;;
        --proxy-port)
            PROXY_PORT="$2"
            shift 2 || fatal "Missing argument to $1"
            ;;
        --proxy-port=*)
            PROXY_PORT="${1#*=}"
            shift
            ;;
        --http-proxy-host)
            HTTP_PROXY_HOST="$2"
            shift 2 || fatal "Missing argument to $1"
            ;;
        --http-proxy-host=*)
            HTTP_PROXY_HOST="${1#*=}"
            shift
            ;;
        --http-proxy-port)
            HTTP_PROXY_PORT="$2"
            shift 2 || fatal "Missing argument to $1"
            ;;
        --http-proxy-port=*)
            HTTP_PROXY_PORT="${1#*=}"
            shift
            ;;
        --catalina-config)
            CATALINA_CONFIG+=("$2")
            shift 2 || fatal "Missing argument to $1"
            ;;
        --catalina-config=*)
            CATALINA_CONFIG+=("${1#*=}")
            shift
            ;;
        --entrypoint-config)
            ENTRYPOINT_CONFIG="$2"
            shift 2 || fatal "Missing argument to $1"
            ;;
        --entrypoint-config=*)
            ENTRYPOINT_CONFIG="${1#*=}"
            shift
            ;;
        --keep-webapps)
            KEEP_WEBAPPS=true
            shift
            ;;
        --help|--help-entrypoint)
            usage
            exit
            ;;
        --)
            shift
            break
            ;;
        -*)
            break
            ;;
        *)
            break
            ;;
    esac
done

info "CONTEXT_PATH=$CONTEXT_PATH"
info "PORT=$PORT"
info "PROXY_NAME=$PROXY_NAME"
info "PROXY_PORT=$PROXY_PORT"
info "HTTP_PROXY_HOST=$HTTP_PROXY_HOST"
info "HTTP_PROXY_PORT=$HTTP_PROXY_PORT"
info "CATALINA_CONFIG=(${CATALINA_CONFIG[*]})"
info "ENTRYPOINT_CONFIG=$ENTRYPOINT_CONFIG"
info "KEEP_WEBAPPS=$KEEP_WEBAPPS"

for ((i = 0; i < ${#CATALINA_CONFIG[@]}; i++)); do
    echo "${CATALINA_CONFIG[i]}" >> "$CATALINA_BASE/bin/setenv.sh" || exit 1
done

if [[ -r "${ENTRYPOINT_CONFIG}" ]]; then
    # shellcheck source=/dev/null
    source "${ENTRYPOINT_CONFIG}"
fi

if [[ -n "$HTTP_PROXY_HOST" ]]; then
    JAVA_OPTS="$JAVA_OPTS -Dhttp.proxyHost=${HTTP_PROXY_HOST}"
fi

if [[ -n "$HTTP_PROXY_PORT" ]]; then
    JAVA_OPTS="$JAVA_OPTS -Dhttp.proxyPort=${HTTP_PROXY_PORT}"
fi

if [[ "$KEEP_WEBAPPS" != "true" ]]; then
    rm -rf "$CATALINA_BASE/webapps/"
fi

# Define server.xml options
CATALINA_OPTS="$CATALINA_OPTS -Dcontext.docBase=$WAR_FILE_PATH -Dcontext.path=$CONTEXT_PATH"

CONNECTOR_PARAMS=

if [[ -n "$PORT" ]]; then
    CONNECTOR_PARAMS="$CONNECTOR_PARAMS port=\"$PORT\""
fi
if [[ -n "$PROXY_NAME" ]]; then
    CONNECTOR_PARAMS="$CONNECTOR_PARAMS proxyName=\"$PROXY_NAME\""
fi
if [[ -n "$PROXY_PORT" ]]; then
    CONNECTOR_PARAMS="$CONNECTOR_PARAMS proxyPort=\"$PROXY_PORT\""
fi

if ! sed -i -e "s|\${CONNECTOR_PARAMS}|${CONNECTOR_PARAMS}|g" "$CONF_DIR/server.xml"; then
    fatal "Could not update $CONF_DIR/server.xml file"
fi

export CATALINA_OPTS JAVA_OPTS

info "JAVA_OPTS=$JAVA_OPTS"
info "CATALINA_OPTS=$CATALINA_OPTS"
info "CONNECTOR_PARAMS=$CONNECTOR_PARAMS"

if [[ $# -eq 0 ]]; then
    set -- catalina.sh run
fi

set -xe
exec /usr/local/bin/tini -- "$@"
