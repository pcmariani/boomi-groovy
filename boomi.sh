#!/usr/bin/env bash

declare -a args

while [[ "$#" -gt 0 ]]; do
	case $1 in
	-t | --test)
		test=$2
		shift
		;;
	-c | --color) color=1 ;;
	-a | --align) align=1 ;;
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
	# echo "exitCode: $exitCode"

elif [[ $color -eq 1 || $align -eq 1 ]]; then
	# echo "color or align"
	if echo "$output" | grep -q -e "Dynamic Document Props" -e "DYNAMIC PROCESS PROPS"; then
		groovy "$BOOMI_GROOVY_HOME"/"$nameOfBoomiGroovy" "${args[@]}" -w "$workingDir"
		exitCode="$?"
		# echo "exitCode: $exitCode"

	else
		output="$(groovy "$BOOMI_GROOVY_HOME"/"$nameOfBoomiGroovy" "${args[@]}" -w "$workingDir")"
		exitCode="$?"
		# echo "exitCode: $exitCode"
		if [[ "$exitCode" != "0" ]]; then
			echo "$output"
		else
			# output="{\"a\":\"b\"}"
			firstLine=$(echo "$output" | head -n1)
			# echo "$firstLine"
			fileType=""
			# firstLine="aaaa	bbbb"
			if [[ -x "$(command -v bat)" && $color -eq 1 ]]; then
				# echo "color"
				if echo "$firstLine" | grep -q '^[[:space:]]*[{[]'; then
					# echo "JSON"
					echo "$output" | bat -pp -l 'JSON'
				elif echo "$firstLine" | grep -q '^[[:space:]]*<'; then
					# echo "JSON"
					echo "$output" | bat -pp -l 'XML'
				elif echo "$firstLine" | grep -q -e ',' -e $'\t'; then
					# echo "CSV tab or comma"
					echo "$output" | bat -pp -l 'Comma Separated Values'
				elif echo "$firstLine" | grep -q '|\^|'; then
					# echo "|^| delimted"
					echo "$output" | sed 's/|\^|/\t/g' | bat -pp -l 'Comma Separated Values'
				fi
			elif [[ $align -eq 1 ]]; then
				# echo "align"
				if echo "$firstLine" | grep -q $'\t'; then
					# echo "CSV -tab"
					echo "$output" | column -t -s $'\t'
				elif echo "$firstLine" | grep -q ','; then
					# echo "CSV -comma"
					echo "$output" | column -t -s ','
				elif echo "$firstLine" | grep -q '|\^|'; then
					# echo "|^| delimted"
					echo "$output" | sed 's/|\^|/\t/g' | column -t -s $'\t'
				fi
			fi
		fi
	fi

else
	groovy "$BOOMI_GROOVY_HOME"/"$nameOfBoomiGroovy" "${args[@]}" -w "$workingDir"
	exitCode="$?"
	# echo "exitCode: $exitCode"
fi

popd >/dev/null

if [[ "$exitCode" != "0" ]]; then
	exit 1
fi
