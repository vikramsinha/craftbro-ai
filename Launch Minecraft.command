#!/bin/zsh
cd -- "${0:A:h}" || exit 1
./gradlew --gradle-user-home .gradle-user-home runClient
result=$?
if (( result != 0 )); then
  printf '\nMinecraft could not start. See the error above.\n'
fi
printf '\nPress Enter to close this window.'
read
exit "$result"
