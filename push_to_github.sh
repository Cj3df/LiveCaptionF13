#!/bin/bash
set -e

if [ -z "$1" ]; then
    echo "Usage: ./push_to_github.sh <GITHUB_REPO_URL_OR_TOKEN>"
    echo "Example 1 (SSH/HTTPS): ./push_to_github.sh https://github.com/username/LiveCaptionF13.git"
    echo "Example 2 (Token): ./push_to_github.sh https://<YOUR_GITHUB_TOKEN>@github.com/username/LiveCaptionF13.git"
    exit 1
fi

REPO_URL="$1"
cd /home/ubuntu/Downloads/agy/LiveCaptionF13

git remote remove origin 2>/dev/null || true
git remote add origin "$REPO_URL"
git branch -M main

echo "Pushing code to GitHub to trigger GitHub Actions APK build..."
git push -u origin main

echo ""
echo "=== PUSH SUCCESSFUL! ==="
echo "GitHub Actions is now automatically building your APK."
echo "Visit your repository's 'Actions' tab on GitHub to download the completed APK."
