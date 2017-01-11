Reflect about your solution!
Summary:

All commands work as they are supposed to. We tested our solution intensively and fixed any bugs detected.

Important additional classes: all classes of the channels package (they encapsulate a socket with the reader and writer)
			      the Cryptography class (encapsulates static methods to generate hashes, keys, secure randoms and other stuff)
			      
Stage1: The nameservers get registrated as they should and they propagate the messages top down.
Stage2: The authenticate method works nicely and uses a secure channel. The password is no more needed.
Stage3: The msg method hashes the message, so that the receiving client can test if the message was changed.
