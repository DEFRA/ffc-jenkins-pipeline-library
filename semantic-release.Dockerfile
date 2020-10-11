ARG PARENT_VERSION=1.2.1-node12.18.3

FROM defradigital/node-development:${PARENT_VERSION}

# Initialise a new project and install dependencies for semantic-release.
# Potentially this could be handled by copying in a pre-created `package.json`.
RUN npm init -y
RUN npm install \
    @commitlint/cli@^11 @commitlint/config-conventional@^11 \
    semantic-release@^17 @semantic-release/changelog@^5 \
    @semantic-release/git@^9 @semantic-release/exec@^5 \
    @google/semantic-release-replace-plugin@^1

WORKDIR /home/node/wrk

# Copy .git/ to .git/, ensures files are readable by node user.
COPY --chown=node:node .git/ .git/
# Copy files required for release.
COPY --chown=node:node scripts/ .releaserc*.json CHANGELOG*.md ./

RUN git fetch --tags -f

ENTRYPOINT [ "npx", "semantic-release" ]
CMD [ "--help" ]
