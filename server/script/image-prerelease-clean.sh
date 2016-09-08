sudo passwd -l root
# remove ssh public keys so that ssh server is disabled
# sudo shred -u /etc/ssh/*_key /etc/ssh/*_key.pub
shred -u ~/.*history
shred -u ~/.gitconfig
