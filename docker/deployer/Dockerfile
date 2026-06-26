ARG IMAGE_REGISTRY_PREFIX=
FROM ${IMAGE_REGISTRY_PREFIX}docker:27-cli

ARG ALPINE_MIRROR=

RUN if [ -n "$ALPINE_MIRROR" ]; then \
      ALPINE_VER=$(cat /etc/alpine-release | cut -d. -f1-2); \
      printf '%s\n' "${ALPINE_MIRROR%/}/v${ALPINE_VER}/main" \
                     "${ALPINE_MIRROR%/}/v${ALPINE_VER}/community" \
        > /etc/apk/repositories; \
    fi && \
    apk add --no-cache git bash curl && \
    docker compose version

WORKDIR /workspace
