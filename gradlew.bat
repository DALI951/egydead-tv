@echo off
setlocal
set DIR=%~dp0
set CLASSPATH=%DIR%gradle\wrapper\gradle-wrapper.jar
java -jar "%CLASSPATH%" --version
endlocal
