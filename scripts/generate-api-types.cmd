@echo off
REM
REM Generate TypeScript types from the backend OpenAPI spec (Windows).
REM
REM Prerequisites:
REM   1. Backend running on http://localhost:8080 (springdoc-openapi enabled)
REM   2. openapi-typescript installed in admin-web and client-web
REM
REM Usage:
REM   scripts\generate-api-types.cmd
REM
REM Optionally pass a backend URL:
REM   scripts\generate-api-types.cmd http://my-backend:8080
REM

setlocal enabledelayedexpansion

set BACKEND_URL=%1
if "%BACKEND_URL%"=="" set BACKEND_URL=http://localhost:8080

set OPENAPI_JSON_URL=%BACKEND_URL%/v3/api-docs
set TEMP_FILE=%TEMP%\openapi.json
set OUTPUT_FILE=schema.d.ts

echo ==^> Fetching OpenAPI spec from %OPENAPI_JSON_URL% ...
curl -fsSL -o "%TEMP_FILE%" "%OPENAPI_JSON_URL%"
if %ERRORLEVEL% neq 0 (
    echo ERROR: Failed to fetch OpenAPI spec. Is the backend running at %BACKEND_URL%?
    exit /b 1
)

echo ==^> Generating TypeScript types for admin-web ...
if not exist "admin-web\src\api\generated" mkdir "admin-web\src\api\generated"
npx --prefix admin-web openapi-typescript "%TEMP_FILE%" -o "admin-web\src\api\generated\%OUTPUT_FILE%"
echo     -^> admin-web\src\api\generated\%OUTPUT_FILE%

echo ==^> Generating TypeScript types for client-web ...
if not exist "client-web\src\api\generated" mkdir "client-web\src\api\generated"
npx --prefix client-web openapi-typescript "%TEMP_FILE%" -o "client-web\src\api\generated\%OUTPUT_FILE%"
echo     -^> client-web\src\api\generated\%OUTPUT_FILE%

del "%TEMP_FILE%" 2>nul
echo ==^> Done. Types generated successfully.

endlocal
