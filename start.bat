:: @echo off

START java -Dcolor -jar build\libs\NetworkingProject-all.jar 1001 > 1001.log 2>&1
timeout 1 > nul
START java -Dcolor -jar build\libs\NetworkingProject-all.jar 1002 > 1002.log 2>&1
:: timeout 1 > nul
:: START java -Dcolor -jar build\libs\NetworkingProject-all.jar 1003
:: timeout 1 > nul
:: START java -Dcolor -jar build\libs\NetworkingProject-all.jar 1004
:: timeout 1 > nul
:: START java -Dcolor -jar build\libs\NetworkingProject-all.jar 1005
:: timeout 1 > nul
:: START java -Dcolor -jar build\libs\NetworkingProject-all.jar 1006
