@echo off
echo === Java Test ===
echo.
echo PATH=%PATH%
echo.
echo Java location:
where java
echo.
echo Java version:
java -version 2>&1
echo.
echo Exit code: %ERRORLEVEL%
pause
