<!-- # NetworkingProject -->
P2P file sharing similar to BitTorrent

**Step 1:** Build (Optional; Pre-Built `.jar` is included)

Linux/Mac:

```
./gradlew shadow
```

Windows:

```
gradlew shadow
```

**Step 2:** Setup directories and files:

`python setup.py`

This will create `peer_1001` to `peer_1006` directories and put `TheFile.dat`
inside `peer_1001` directory. It is recommended to run this script before every
run (in case your previous run left some partially downloaded portions of
TheFile.dat, it will clean them).

**Step 3:** Run all clients

On Windows: `start.bat`

On Linux/OSX: `bash start.sh`

To run independently: `java -Dcolor -jar build/libs/NetworkingProject-all.jar <peer_id>`
