#!/usr/bin/env bash
# Installs the current GitHub CLI from GitHub's official apt repository.
# (Ubuntu's own 'gh' package is years old and too old for these scripts.)
#   ./scripts/install-gh.sh
set -euo pipefail

command -v wget >/dev/null 2>&1 || sudo apt install -y wget
sudo mkdir -p -m 755 /etc/apt/keyrings
wget -qO- https://cli.github.com/packages/githubcli-archive-keyring.gpg | sudo tee /etc/apt/keyrings/githubcli-archive-keyring.gpg >/dev/null
sudo chmod go+r /etc/apt/keyrings/githubcli-archive-keyring.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" \
  | sudo tee /etc/apt/sources.list.d/github-cli.list >/dev/null
sudo apt update
sudo apt install -y gh
echo
gh --version | head -1
