import os, shutil
from ctypes import c_byte

val = 50  # Just any non-zero value
file_size = 10000232 #bytes
file_name = ('TheFile.dat')

dir = ['peer_' + str(i) for i in range(1001, 1007)]

# Removing directories and their contents for a clean start
for dirname in dir:
    if os.path.exists(dirname):
        shutil.rmtree(dirname)
        
# Creating directories
for dirname in dir:
    if not os.path.exists(dirname):
        os.mkdir(dirname)           

# Placing file inside first client i.e. 1001      

file = os.path.join(dir[0], file_name)
buffer = (c_byte * file_size) (*[val] * file_size)
open(file, 'wb').write(buffer)
    
