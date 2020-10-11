#!/usr/bin/env bash

TAG=$1
SHA=$2
MAJOR=$(echo "$TAG" | cut -d. -f1)
MINOR=$(echo "$TAG" | cut -d. -f1,2)

echo "Updating MAJOR ($MAJOR) tag to point to $SHA"
git push origin ":refs/tags/$MAJOR"
git tag -f "$MAJOR" "$SHA"
git push origin "$MAJOR"

echo "Updating MINOR ($MINOR) tag to point to $SHA"
git push origin ":refs/tags/$MINOR"
git tag -f "$MINOR" "$SHA"
git push origin "$MINOR"
