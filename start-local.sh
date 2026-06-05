#!/bin/bash

# ============================================================
# Portfolio - Local Development Startup Script
# ============================================================

# ── Colori per output ─────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# ── Funzioni di utilità ───────────────────────────────────
print_banner() {
  echo ""
  echo -e "${BLUE}==========================================${NC}"
  echo -e "${BLUE}  Portfolio - Local Development${NC}"
  echo -e "${BLUE}==========================================${NC}"
  echo ""
}

print_step() {
  echo -e "${GREEN}[$1]${NC} $2"
}

print_error() {
  echo -e "${RED}[ERROR]${NC} $1"
}

print_warning() {
  echo -e "${YELLOW}[WARN]${NC} $1"
}

# ── Cleanup function ──────────────────────────────────────
cleanup() {
  echo ""
  echo ""
  print_step "STOP" "Arresto del server..."
  
  # Termina sbt se ancora in esecuzione
  if [ ! -z "$SBT_PID" ]; then
    kill -TERM "$SBT_PID" 2>/dev/null
    wait "$SBT_PID" 2>/dev/null
  fi
  
  print_step "STOP" "Server arrestato ✓"
  exit 0
}

# Cattura Ctrl+C e altri segnali di terminazione
trap cleanup SIGINT SIGTERM

# ── Banner ─────────────────────────────────────────────────
print_banner

# ── Variabili d'ambiente ──────────────────────────────────
print_step "ENV" "Caricamento variabili d'ambiente da .env..."

# Carica .env se esiste
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -f "$SCRIPT_DIR/.env" ]; then
  set -a
  source "$SCRIPT_DIR/.env"
  set +a
  print_step "ENV" "File .env caricato ✓"
else
  print_error "File .env non trovato in $SCRIPT_DIR"
  exit 1
fi

# Imposta valori predefiniti per variabili non presenti nel .env
export GITHUB_REPO="${GITHUB_REPO:-portfolio}"
export GITHUB_BRANCH="${GITHUB_BRANCH:-main}"
export SMTP_HOST="${SMTP_HOST:-smtp.resend.com}"
export SMTP_PORT="${SMTP_PORT:-587}"
export SMTP_USER="${SMTP_USER:-resend}"
export SMTP_FROM="${SMTP_FROM:-onboarding@resend.dev}"
export CONTENT_BASE_PATH="${CONTENT_BASE_PATH:-src/main/resources}"
export SESSION_EXPIRY_HOURS="${SESSION_EXPIRY_HOURS:-8}"
export SMTP_TO="${SMTP_TO:-$ADMIN_EMAIL}"

# Verifica variabili obbligatorie
MISSING_VARS=()

if [ -z "$GITHUB_TOKEN" ]; then
  MISSING_VARS+=("GITHUB_TOKEN")
fi

if [ -z "$SMTP_PASSWORD" ]; then
  MISSING_VARS+=("SMTP_PASSWORD")
fi

if [ -z "$GITHUB_OWNER" ]; then
  MISSING_VARS+=("GITHUB_OWNER")
fi

if [ -z "$ADMIN_EMAIL" ]; then
  MISSING_VARS+=("ADMIN_EMAIL")
fi

if [ ${#MISSING_VARS[@]} -gt 0 ]; then
  print_error "Variabili obbligatorie mancanti nel file .env:"
  for var in "${MISSING_VARS[@]}"; do
    echo "  - $var"
  done
  exit 1
fi

echo ""
print_step "ENV" "Configurazione:"
echo "  GITHUB_OWNER  = $GITHUB_OWNER"
echo "  GITHUB_REPO   = $GITHUB_REPO"
echo "  GITHUB_BRANCH = $GITHUB_BRANCH"
echo "  ADMIN_EMAIL   = $ADMIN_EMAIL"
echo "  SMTP_HOST     = $SMTP_HOST"
echo "  SMTP_FROM     = $SMTP_FROM"
echo ""

# ── Scelta modalità compilazione ─────────────────────────
echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${YELLOW}  Modalità di compilazione:${NC}"
echo -e "${YELLOW}  1) Compilazione normale${NC}"
echo -e "${YELLOW}  2) Compilazione verbosa (-explain)${NC}"
echo -e "${YELLOW}  3) Salta compilazione${NC}"
echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

read -p "  Scegli [1-3] (default: 1): " BUILD_MODE
BUILD_MODE=${BUILD_MODE:-1}
echo ""

# ── Compilazione ─────────────────────────────────────────
case $BUILD_MODE in
  1)
    print_step "BUILD" "Compilazione in corso..."
    sbt compile
    print_step "BUILD" "Compilazione completata ✓"
    ;;
  2)
    print_step "BUILD" "Compilazione verbosa in corso..."
    sbt 'set Compile / scalacOptions += "-explain"' compile
    print_step "BUILD" "Compilazione completata ✓"
    ;;
  3)
    print_warning "Compilazione saltata"
    ;;
  *)
    print_error "Scelta non valida"
    exit 1
    ;;
esac

echo ""

# ── Avvio ────────────────────────────────────────────────
print_step "RUN" "Avvio del server su ${BLUE}http://localhost:8080${NC}"
echo ""
echo -e "${GREEN}  Premi Ctrl+C per fermare il server${NC}"
echo ""

# Avvia sbt in background e salva il PID
sbt run &
SBT_PID=$!

# Aspetta che sbt termini
wait $SBT_PID 2>/dev/null

# Cleanup dopo uscita normale
cleanup