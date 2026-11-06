#!/bin/sh
set -eu

version="${1:?Chrome for Testing version is required}"
case "$version" in
  *[!0-9.]*|'') echo 'Invalid Chromium version' >&2; exit 1 ;;
esac
case "$(dpkg --print-architecture)" in
  amd64) platform=linux64; archive=chrome-linux64 ;;
  arm64) platform=linux-arm64; archive=chrome-linux-arm64 ;;
  *) echo 'Unsupported Chromium architecture' >&2; exit 1 ;;
esac

apt-get update
apt-get install --yes --no-install-recommends \
  ca-certificates curl unzip util-linux fonts-liberation \
  libasound2 libatk-bridge2.0-0 libatk1.0-0 libatspi2.0-0 libcairo2 libcups2 \
  libdbus-1-3 libdrm2 libexpat1 libgbm1 libglib2.0-0 libgtk-3-0 libnspr4 \
  libnss3 libpango-1.0-0 libudev1 libvulkan1 libx11-6 libxcb1 libxcomposite1 \
  libxdamage1 libxext6 libxfixes3 libxkbcommon0 libxrandr2
curl --fail --silent --show-error --location --retry 3 \
  "https://storage.googleapis.com/chrome-for-testing-public/$version/$platform/$archive.zip" \
  --output /tmp/chrome.zip
unzip -q /tmp/chrome.zip -d /opt
mv "/opt/$archive" /opt/chrome
chmod -R a+rX /opt/chrome
rm -f /tmp/chrome.zip
rm -rf /var/lib/apt/lists/*
