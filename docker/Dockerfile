# Production image for Suwayomi Exp Server (ExpUI baked in).
#
# Build context: repository root, after preparing:
#   docker/server.jar   — ./gradlew :server:shadowJar
#   docker/expui/       — static export of suwayomi-exp-ui (index.html + assets)
#
# Prefer: scripts/docker/build.sh
#
# Runtime data: /home/suwayomi/.local/share/Tachidesk
# Env vars: same as Suwayomi-Server-docker (FLARESOLVERR_*, BIND_*, …)
# Defaults: WEB_UI_FLAVOR=ExpUI, WEB_UI_UPDATE_INTERVAL=0, KCEF_ENABLED=false

FROM eclipse-temurin:21-jre-noble

ARG BUILD_DATE
ARG VERSION=dev
ARG REVISION=0
ARG VCS_REF

LABEL org.opencontainers.image.title="Suwayomi Exp Server" \
      org.opencontainers.image.description="Suwayomi Exp Server with baked-in ExpUI" \
      org.opencontainers.image.source="https://github.com/Vinicius-moura-code/suwayomi-exp-server" \
      org.opencontainers.image.licenses="MPL-2.0" \
      org.opencontainers.image.created="${BUILD_DATE}" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${VCS_REF}"

RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      curl \
      gettext-base \
      unzip \
      tini \
      ca-certificates \
      p11-kit \
      perl \
 && /usr/bin/p11-kit extract --format=java-cacerts --filter=certificates --overwrite --purpose server-auth \
      "$JAVA_HOME/lib/security/cacerts" \
 && apt-get clean \
 && rm -rf /var/lib/apt/lists/*

# Drop default ubuntu user if present; create suwayomi (uid 1000)
RUN (userdel -r ubuntu 2>/dev/null || true) \
 && groupadd --gid 1000 suwayomi \
 && useradd --uid 1000 --gid suwayomi --no-log-init --create-home suwayomi \
 && mkdir -p /home/suwayomi/.local/share/Tachidesk /home/suwayomi/startup /opt/suwayomi-exp/webUI

COPY docker/scripts/create_server_conf.sh /home/suwayomi/create_server_conf.sh
COPY docker/scripts/startup_script.sh /home/suwayomi/startup_script.sh
COPY docker/scripts/seed_expui.sh /home/suwayomi/seed_expui.sh
COPY docker/expui/ /opt/suwayomi-exp/webUI/
COPY docker/server.jar /home/suwayomi/startup/tachidesk_latest.jar

RUN chmod 755 /home/suwayomi/create_server_conf.sh \
              /home/suwayomi/startup_script.sh \
              /home/suwayomi/seed_expui.sh \
 && chmod 777 -R /home/suwayomi \
 && chown -R suwayomi:suwayomi /home/suwayomi /opt/suwayomi-exp

ENV HOME=/home/suwayomi \
    TZ=Etc/UTC \
    WEB_UI_FLAVOR=EXPUUI \
    WEB_UI_UPDATE_INTERVAL=0 \
    KCEF_ENABLED=false

WORKDIR /home/suwayomi
USER suwayomi
EXPOSE 4567

ENTRYPOINT ["tini", "--"]
CMD ["/home/suwayomi/startup_script.sh"]
