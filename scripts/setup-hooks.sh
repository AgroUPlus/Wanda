#!/usr/bin/env bash
# scripts/setup-hooks.sh
# Configures git to use .githooks as core.hooksPath for local verification.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if ! command -v git &>/dev/null; then
  echo "Git is required to configure hooks."
  exit 1
fi

git config core.hooksPath .githooks
chmod +x .githooks/* scripts/setup-hooks.sh

echo -e "\033[0;32m✓ Git hooks configured successfully (.githooks)\033[0m"
echo "  - .githooks/commit-msg: Enforces Conventional Commits & blocks AI trailers"
echo "  - .githooks/pre-commit: Blocks merge conflicts & sensitive keystores"
