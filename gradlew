#!/bin/sh
if command -v gradle >/dev/null 2>&1; then
    exec gradle "$@"
else
    echo "Gradle not found, installing..."
    curl -s https://get.sdkman.io | bash
    source "$HOME/.sdkman/bin/sdkman-init.sh"
    sdk install gradle 8.2
    exec gradle "$@"
fi
