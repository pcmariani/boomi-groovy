#!/usr/bin/env bash

workingDir="$(pwd)"
# echo "shell workdingDir: $workingDir"
# echo "shell BOOMI_SCRIPT_TESTER_DIRECTORY: $BOOMI_SCRIPT_TESTER_DIRECTORY"

pushd $BOOMI_SCRIPT_TESTER_DIRECTORY > /dev/null
# groovy "$BOOMI_SCRIPT_TESTER_DIRECTORY"/BoomiTestBed.groovy $@ -w "$workingDir"
groovy "$BOOMI_SCRIPT_TESTER_DIRECTORY"/"$BOOMI_SCRIPT_TESTER_FILENAME" $@ -w "$workingDir"
exitCode="$?"

popd > /dev/null

if [[ "$exitCode" != "0" ]]; then
    exit 1
fi
