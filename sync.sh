#!/bin/bash

# ============================================================
# Portfolio - Sync Script
# Sincronizza modifiche remote (admin) e locali (filesystem)
# ============================================================

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo ""
echo -e "${BLUE}==========================================${NC}"
echo -e "${BLUE}  Portfolio - Sync${NC}"
echo -e "${BLUE}==========================================${NC}"
echo ""

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 1. Scarica modifiche remote (fatte dall'admin online)
echo -e "${GREEN}[1/4]${NC} Download modifiche remote..."
git pull
echo ""

# 2. Mostra cosa è cambiato
echo -e "${GREEN}[2/4]${NC} Modifiche locali rilevate:"
if git diff --quiet && git diff --cached --quiet; then
  echo -e "  ${YELLOW}(nessuna modifica locale)${NC}"
else
  git status --short
fi
echo ""

# 3. Commit e push modifiche locali
echo -e "${GREEN}[3/4]${NC} Upload modifiche locali..."
git add -A
if git commit -m "sync: $(date '+%Y-%m-%d %H:%M')" 2>/dev/null; then
  echo "  Commit creato ✓"
else
  echo -e "  ${YELLOW}(nessuna modifica da committare)${NC}"
fi
echo ""

# 4. Push
echo -e "${GREEN}[4/4]${NC} Push su GitHub..."
git push
echo ""

echo -e "${GREEN}✅ Sincronizzazione completata!${NC}"
echo ""