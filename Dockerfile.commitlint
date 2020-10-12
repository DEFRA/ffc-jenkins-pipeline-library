ARG PARENT_VERSION=1.2.1-node12.18.3

FROM defradigital/node-development:${PARENT_VERSION}

# Initialise a new project and install dependencies for commitlint.
# Potentially this could be handled by copying in a pre-created `package.json`.
RUN npm init -y
RUN npm install \
    @commitlint/cli@^11 @commitlint/config-conventional@^11

WORKDIR /home/node/wrk

ENTRYPOINT [ "npx", "commitlint" ]
CMD [ "--help" ]
