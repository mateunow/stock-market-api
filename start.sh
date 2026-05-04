#!/bin/sh
# Usage: ./start.sh <PORT>
PORT=${1:-8080}
PORT=$PORT docker compose up --build -d
echo "Stock Market API running at http://localhost:$PORT"
