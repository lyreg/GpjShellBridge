GpjShellBridge
==============

connects and allows a shell to a remote simplytapp cloud card


About
=====
this application allows you to connect to a single cloud card
within the simplytapp domain
it can be used as an interactive shell or be passed in a script

Paramaters
==========
-ck  the issuer consumer key
-cs  the issuer consumer secret
-at  the card access token
-ts  the card access token secret
-p the port number to run the local service on (optional)
-i  the interface to attach to the card (T0 or NFC)
-s the .jcsh script to run
-noshell  (do this if you would like to use your own Shell tool to connect to the cloud through JCRemoteTermial)
