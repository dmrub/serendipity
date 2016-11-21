FROM maven:3.3.9-jdk-8

ARG APP_NAME=app
RUN mkdir -p /usr/src/$APP_NAME
WORKDIR /usr/src/$APP_NAME

ADD . /usr/src/$APP_NAME

ARG MAVEN_LOCAL_REPO=/usr/share/m2
ENV MAVEN_LOCAL_REPO "${MAVEN_LOCAL_REPO}"
RUN mkdir -p "$MAVEN_LOCAL_REPO"

RUN for d in deps/*; do \
      if [ -d "$d" ]; then \
        (cd "$d"; mvn -Dmaven.repo.local="$MAVEN_LOCAL_REPO" clean install) || exit 1; \
      fi; \
    done

RUN mvn -Dmaven.repo.local="$MAVEN_LOCAL_REPO" clean package install tomcat7:help
CMD mvn -Dmaven.repo.local="$MAVEN_LOCAL_REPO" tomcat7:run
