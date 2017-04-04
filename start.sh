java -Dcolor -Dpeerid=1001 -jar build/libs/NetworkingProject-all.jar 1001 &
sleep 1
java -Dcolor -Dpeerid=1002 -jar build/libs/NetworkingProject-all.jar 1002 &
sleep 1
java -Dcolor -Dpeerid=1003 -jar build/libs/NetworkingProject-all.jar 1003 &
sleep 1
java -Dcolor -Dpeerid=1004 -jar build/libs/NetworkingProject-all.jar 1004 &
sleep 1
java -Dcolor -Dpeerid=1005 -jar build/libs/NetworkingProject-all.jar 1005 &
sleep 1
java -Dcolor -Dpeerid=1006 -jar build/libs/NetworkingProject-all.jar 1006 &

wait

pkill $$
