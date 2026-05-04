@echo off
SET PORT=%1
IF "%PORT%"=="" SET PORT=8080
SET PORT=%PORT%
docker compose up --build -d
echo Stock Market API running at http://localhost:%PORT%
