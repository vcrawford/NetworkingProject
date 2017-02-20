java -Dcolor -jar build/libs/NetworkingProject-all.jar 1001 &
sleep 1
java -Dcolor -jar build/libs/NetworkingProject-all.jar 1002 &
sleep 1
java -Dcolor -jar build/libs/NetworkingProject-all.jar 1003 &
sleep 1
java -Dcolor -jar build/libs/NetworkingProject-all.jar 1004 &
sleep 1
java -Dcolor -jar build/libs/NetworkingProject-all.jar 1005 &
sleep 1
java -Dcolor -jar build/libs/NetworkingProject-all.jar 1006

pkill -P $$
