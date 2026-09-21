#!/bin/zsh
cd -- "${0:A:h}" || exit 1
/usr/bin/python3 scripts/relaunch.py
result=$?
if (( result != 0 )); then
  printf '\nPress Enter to close this window.'
  read
fi
exit "$result"
