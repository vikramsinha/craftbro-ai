#!/bin/zsh
cd -- "${0:A:h}" || exit 1
set -e
trap 'printf "\nLocal AI setup failed. See the error above. Press Enter to close."; read' ZERR
mkdir -p .tools/ollama .tools/ollama-models
if [[ ! -x .tools/ollama/ollama ]]; then
  printf 'Downloading Ollama for Mac...\n'
  curl -fL https://github.com/ollama/ollama/releases/download/v0.34.2/ollama-darwin.tgz -o .tools/ollama/ollama-darwin.tgz
  tar -xzf .tools/ollama/ollama-darwin.tgz -C .tools/ollama
fi
export OLLAMA_HOST=127.0.0.1:11434
export OLLAMA_MODELS="$PWD/.tools/ollama-models"
export OLLAMA_NO_CLOUD=1
export OLLAMA_NUM_PARALLEL=1
export OLLAMA_MAX_LOADED_MODELS=1
ollama_server_pid=''
cleanup() { if [[ -n "$ollama_server_pid" ]]; then kill "$ollama_server_pid" 2>/dev/null || true; fi; }
trap cleanup EXIT
trap 'exit 130' INT TERM
if ! curl -fsS --max-time 2 http://127.0.0.1:11434/api/version >/dev/null 2>&1; then
  .tools/ollama/ollama serve >.tools/ollama/server.log 2>&1 &
  ollama_server_pid=$!
  for attempt in {1..30}; do
    if curl -fsS --max-time 2 http://127.0.0.1:11434/api/version >/dev/null 2>&1; then break; fi
    sleep 1
  done
  curl -fsS --max-time 2 http://127.0.0.1:11434/api/version >/dev/null
fi
printf '\nPreparing Qwen 3.5 4B (first download is about 3.4 GB)...\n'
if ! .tools/ollama/ollama show qwen3.5:4b >/dev/null 2>&1; then
  .tools/ollama/ollama pull qwen3.5:4b
fi
printf '\nReady! Launch Minecraft with Telemetry, then use /askmod or /aitip.\nKeep this window open. Press Ctrl+C to stop the server started here.\n'
if [[ -n "$ollama_server_pid" ]]; then
  wait "$ollama_server_pid"
else
  printf 'Using an already-running Ollama server. Press Enter to close this window.\n'
  read
fi
