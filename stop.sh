#!/usr/bin/env bash
echo "Stopping DocReader.ai services..."
fuser -k 8080/tcp 2>/dev/null || true
fuser -k 4200/tcp 2>/dev/null || true
echo "✓ Services stopped."
