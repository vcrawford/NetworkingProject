import os
import numpy as np

val = 50  # Same value as in setup.py
file_size = 10000232 #bytes
file_name = ('TheFile.dat')

dir = ['peer_' + str(i) for i in range(1001, 1007)]
      
for peer_dir in dir:
    file = os.path.join(peer_dir, file_name)
    if os.path.exists(file):
        buffer = open(file, 'rb').read()
        np_buffer = np.frombuffer(buffer, dtype='uint8')
        if np_buffer.shape[0] != file_size:
            print('File size mismatch. Expected: %d found: %d' % (file_size, np_buffer.shape[0]))
        
        match = np.all(np_buffer == val)
        print('Client %s match: ' %  peer_dir, match)
    else:
        print('Client %s match: ' %  peer_dir, 'File %s not found' % file)
    
