:: @echo off

START java -Dcolor -Dpeerid=1001 -jar build\libs\NetworkingProject-all.jar 1001
timeout 1 > nul
START java -Dcolor -Dpeerid=1002 -jar build\libs\NetworkingProject-all.jar 1002
:: timeout 1 > nul
:: START java -Dcolor -jar build\libs\NetworkingProject-all.jar 1003
:: timeout 1 > nul
:: START java -Dcolor -jar build\libs\NetworkingProject-all.jar 1004
:: timeout 1 > nul
:: START java -Dcolor -jar build\libs\NetworkingProject-all.jar 1005
:: timeout 1 > nul
:: START java -Dcolor -jar build\libs\NetworkingProject-all.jar 1006
