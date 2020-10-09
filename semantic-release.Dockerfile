ARG PARENT_VERSION=1.2.1-node12.18.3

FROM defradigital/node-development:${PARENT_VERSION}

# Initialise a new project and install dependencies for semantic-release.
# Potentially this could be handled by copying in a pre-created `package.json`.
RUN npm init -y
RUN npm install \
    @commitlint/cli@^11 @commitlint/config-conventional@^11 \
    semantic-release@^17 @semantic-release/changelog@^5 @semantic-release/git@^9 \
    @google/semantic-release-replace-plugin@^1

WORKDIR /home/node/wrk

# Copy in .git dir to ensure files are accessible
COPY --chown=node:node .git/ .git/

RUN git fetch --tags -f

ENTRYPOINT [ "npx", "semantic-release" ]
CMD [ "--help" ]
