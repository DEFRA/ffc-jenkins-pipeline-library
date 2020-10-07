ARG PARENT_VERSION=1.2.1-node12.18.3

FROM defradigital/node-development:${PARENT_VERSION}

# init new project and install dependencies for commitlint
# potentially this could be handled by copying in a pre-created `package.json`
# consider versioning the deps, as in, do it!
RUN npm init -y
RUN npm install \
    @commitlint/cli @commitlint/config-conventional

WORKDIR /home/node/wrk
ENTRYPOINT [ "npx", "commitlint" ]
CMD [ "--help" ]
