#!/usr/bin/env bash

declare -a args

while [[ "$#" -gt 0 ]]; do
	case $1 in
	-t | --test)
		test=$2
		shift
		;;
	-c | --color) color=1 ;;
	*) args+=($1) ;;
	esac
	shift
done
# echo "args: ${args[@]}"

nameOfBoomiGroovy="boomi.groovy"
nameOfBoomiGroovyTest="boomi-test.groovy"

workingDir="$(pwd)"
# echo "workdingDir: $workingDir"
# echo "BOOMI_GROOVY_HOME: $BOOMI_GROOVY_HOME"

pushd $BOOMI_GROOVY_HOME >/dev/null

if [[ -n $test ]]; then
	groovy "$BOOMI_GROOVY_HOME"/"$nameOfBoomiGroovyTest" "${args[@]}" -f "$test" -w "$workingDir"
	exitCode="$?"

elif [ -x "$(command -v bat)" && $color -eq 1]; then
	output="$(groovy "$BOOMI_GROOVY_HOME"/"$nameOfBoomiGroovy" "${args[@]}" -w "$workingDir")"
	exitCode="$?"
	# echo "$output"
	if echo "$output" | grep -q -e "Dynamic Document Props" -e "DYNAMIC PROCESS PROPS"; then
		# echo "has ddps"
	else
		# output="{\"a\":\"b\"}"
		firstLine=$(echo "$output" | head -n1)
		# echo "$firstLine"
		fileType=""
		# firstLine="aaaa	bbbb"
		if echo "$firstLine" | grep -q '^[[:space:]]*[{[]'; then
			# echo "JSON"
			echo "$output" | bat -pp -l 'JSON'
		elif echo "$firstLine" | grep -q '^[[:space:]]*<'; then
			# echo "JSON"
			echo "$output" | bat -pp -l 'XML'
		elif echo "$firstLine" | grep -q -e ',' -e $'\t'; then
			# echo "CSV"
			echo "$output" | bat -pp -l 'Comma Separated Values'
		elif echo "$firstLine" | grep -q '|\^|'; then
			# echo "CSV"
			echo "$output" | sed 's/|\^|/\t/g' | bat -pp -l 'Comma Separated Values' | column -t -s $'\t' #| sed 's/\t/|\^|/g'
		fi
	fi

else
	groovy "$BOOMI_GROOVY_HOME"/"$nameOfBoomiGroovy" "${args[@]}" -w "$workingDir"
	exitCode="$?"
fi

popd >/dev/null

if [[ "$exitCode" != "0" ]]; then
	exit 1
fi
