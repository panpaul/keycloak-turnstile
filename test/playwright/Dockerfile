ARG PLAYWRIGHT_VERSION=v1.45.0-noble

FROM mcr.microsoft.com/playwright:${PLAYWRIGHT_VERSION}

ARG UID=1002
ARG GID=1002

RUN addgroup --gid ${GID} playwright
RUN adduser --uid ${UID} --gid ${GID} --shell /bin/bash playwright
USER playwright