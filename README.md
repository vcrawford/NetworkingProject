# NetworkingProject
P2P file sharing similar to BitTorrent

**Step 1:** `cd` to the directory where you've kept your `PeerProcess.java` file.

**Step 2:** Setup directories and files:

`python setup.py`

This will create `peer_1001` to `peer_1006` directories and put `TheFile.dat` inside `peer_1001` directory. It is recommended to run this script before every run (in case your previous run left some remanant downloaded portions of TheFile.dat, it will clean them).

**Step 3:** Compile

`javac PeerProcess.java`

**Step 4:** Run all clients

`start.bat`
