ARG PARENT_VERSION=1.2.1-node12.18.3

FROM defradigital/node-development:${PARENT_VERSION}

# init new project and install dependencies for semantic-release
# potentially this could be handled by copying in a pre-created `package.json`
# consider versioning the deps, as in, it they need versioning!
RUN npm init -y
RUN npm install \
    @commitlint/cli @commitlint/config-conventional \
    semantic-release @semantic-release/changelog @semantic-release/git \
    @google/semantic-release-replace-plugin

WORKDIR /home/node/wrk

# Copy in .git dir to ensure files are accessible
COPY --chown=node:node .git/ .git/

ENTRYPOINT [ "npx", "semantic-release" ]
CMD [ "--help" ]
