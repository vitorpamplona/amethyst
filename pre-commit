#!/bin/bash

GREEN='\033[0;32m'
NO_COLOR='\033[0m'

echo "*********************************************************"
echo "Running git pre-commit hook. Running Static analysis... "
echo "*********************************************************"

./gradlew ktlintCheck

status=$?

if [ "$status" = 0 ] ; then
    echo "Static analysis found no problems."
    exit 0
else
    echo "*********************************************************"
    echo 1>&2 "Static analysis found violations it could not fix."
    printf "Run ${GREEN}./gradlew ktlintFormat${NO_COLOR} to fix formatting related issues...\n"
    echo "*********************************************************"
    exit 1
fi
