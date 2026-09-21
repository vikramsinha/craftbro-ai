#!/bin/zsh
cd -- "${0:A:h}" || exit 1
set -e
trap 'printf "\nBrowser chat could not start. See the error above. Press Enter to close."; read' ZERR
if [[ ! -x .tools/webui/bin/open-webui ]]; then
  printf 'Open WebUI is not installed in this project. See README.md.\n'
  exit 1
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
export DATA_DIR="$PWD/.tools/webui-data"
mkdir -p "$DATA_DIR"
if [[ ! -f "$DATA_DIR/session-secret" ]]; then
  (umask 077; .tools/webui/bin/python -c 'import secrets; print(secrets.token_hex(32))' > "$DATA_DIR/session-secret")
fi
export WEBUI_SECRET_KEY="$(cat "$DATA_DIR/session-secret")"
export WEBUI_AUTH=False
export CORS_ALLOW_ORIGIN='http://localhost:3000;http://127.0.0.1:3000'
export OLLAMA_BASE_URL=http://127.0.0.1:11434
export ENABLE_OPENAI_API=False
export DEFAULT_MODELS=qwen3.5:4b
export DEFAULT_MODEL_PARAMS='{"think":false,"num_ctx":4096,"num_predict":256}'
export DEFAULT_MODEL_METADATA='{"capabilities":{"builtin_tools":false}}'
export ENABLE_TITLE_GENERATION=False
export ENABLE_TAGS_GENERATION=False
export ENABLE_FOLLOW_UP_GENERATION=False
export OFFLINE_MODE=true
export HF_HUB_OFFLINE=1
export RAG_EMBEDDING_ENGINE=ollama
export SCARF_NO_ANALYTICS=true
export DO_NOT_TRACK=true
export ANONYMIZED_TELEMETRY=False
printf '\nBrowser chat: http://localhost:3000\nKeep this window open. Press Ctrl+C to stop.\n'
.tools/webui/bin/open-webui serve --host 127.0.0.1 --port 3000
