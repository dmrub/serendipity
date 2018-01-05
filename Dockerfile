FROM tomcat:8.5-jre8

# grab tini for signal processing and zombie killing
ENV TINI_VERSION v0.16.1
RUN set -x \
        && apt-get update \
        && apt-get install -y --no-install-recommends wget ca-certificates \
        && rm -rf /var/lib/apt/lists/* \
        && wget -O /usr/local/bin/tini "https://github.com/krallin/tini/releases/download/$TINI_VERSION/tini" \
        && wget -O /usr/local/bin/tini.asc "https://github.com/krallin/tini/releases/download/$TINI_VERSION/tini.asc" \
        && export GNUPGHOME="$(mktemp -d)" \
        && gpg --keyserver ha.pool.sks-keyservers.net --recv-keys 6380DC428747F6C393FEACA59A84159D7001A4E5 \
        && gpg --batch --verify /usr/local/bin/tini.asc /usr/local/bin/tini \
        && rm -r "$GNUPGHOME" /usr/local/bin/tini.asc \
        && chmod +x /usr/local/bin/tini \
        && tini -h

ARG WAR_FILE
ENV WAR_FILE="${WAR_FILE}" WAR_FILE_PATH="/opt/webapps/$WAR_FILE"

COPY target/$WAR_FILE "$WAR_FILE_PATH"
COPY server.xml /usr/local/tomcat/conf/
COPY tomcat-users.xml /usr/local/tomcat/conf/
COPY entrypoint.sh /usr/local/bin/

# RUN rm -rf /usr/local/tomcat/webapps
RUN rm -rf /usr/local/tomcat/webapps/ROOT*

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
CMD ["catalina.sh", "run"]
EXPOSE 8080 8009
