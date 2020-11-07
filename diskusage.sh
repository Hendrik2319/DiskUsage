#!/bin/bash 
file=hdd.$(date +%Y.%m.%d.%H.%M.%S).diskusage
du -a /media/hdd/ > $file
mv $file /media/hdd/
