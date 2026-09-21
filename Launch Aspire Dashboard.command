#!/bin/zsh
cd -- "${0:A:h}" || exit 1
if [[ ! -x .tools/aspire/aspire ]]; then
  mkdir -p .tools/aspire || exit 1
  curl -fsSL https://aspire.dev/install.sh -o .tools/aspire/install.sh &&
    bash .tools/aspire/install.sh --install-path "$PWD/.tools/aspire" --skip-path
  if (( $? != 0 )); then
    printf '\nAspire download failed. Check your internet connection. Press Enter to close.'
    read
    exit 1
  fi
fi
export ASPIRE_CLI_TELEMETRY_OPTOUT=1
# Prepare the bundle and copy web assets outside Aspire's hidden cache. Aspire
# marks cache contents hidden during startup, which ASP.NET refuses to serve.
.tools/aspire/aspire dashboard run --help >/dev/null || exit 1
dashboard_assets=( "$PWD"/.tools/versions/*/managed/wwwroot(Nom[1]) )
if (( ${#dashboard_assets} == 0 )); then
  printf '\nAspire dashboard page files were not found. Press Enter to close.'
  read
  exit 1
fi
mkdir -p build/aspire-wwwroot || exit 1
cp -R "$dashboard_assets[1]/." build/aspire-wwwroot/ || exit 1
chflags -R nohidden build/aspire-wwwroot || exit 1
export ASPNETCORE_WEBROOT="$PWD/build/aspire-wwwroot"
printf '\nOpen the dashboard login link printed below. Keep this window open.\n'
.tools/aspire/aspire dashboard run --frontend-url http://127.0.0.1:18888 --otlp-http-url http://127.0.0.1:4318 --otlp-grpc-url http://127.0.0.1:4317
result=$?
if (( result != 0 )); then
  printf '\nDashboard startup failed. See the error above. Press Enter to close.'
  read
fi
exit "$result"
