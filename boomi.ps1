# $DebugPreference = "Continue"
$DebugPreference = "ContinueSilently"

Write-Debug -Message "args: $args"

$workingDir = (pwd).Path
Write-Debug -Message "workingDir: $workingDir"

Push-Location -Path "$env:BOOMI_GROOVY_HOME"
Write-Debug -Message "pwd: $(pwd)"

Write-Debug -Message "to be executed: groovy $env:BOOMI_GROOVY_HOME\boomi.groovy $args -w $workingDir"
groovy "$env:BOOMI_GROOVY_HOME\boomi.groovy" $args -w $workingDir

Pop-Location
Write-Debug -Message "pwd: $(pwd)"

# Set Environment Variables for your User:
# https://stackoverflow.com/questions/67843302/how-can-i-create-user-environment-variables-for-a-non-admin-user-account
# - BOOMI_GROOVY_HOME: dir contaning boomi.groovy
# - Path: Add dir containing boomi.ps1
