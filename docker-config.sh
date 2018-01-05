############### Configuration ###############

APP_NAME=serendipity
IMAGE_PREFIX=${IMAGE_PREFIX:-serendipity}
WAR_FILE=$APP_NAME.war
BUILD_PREFIX=${BUILD_PREFIX:-$IMAGE_PREFIX}
IMAGE_TAG=${IMAGE_TAG:-latest}
IMAGE_NAME=${IMAGE_PREFIX}:${IMAGE_TAG}

JAVA_MAVEN_DEPS=(
    https://github.com/rmrschub/igraphstore
)

############# End Configuration #############
