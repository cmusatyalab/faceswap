sudo passwd -l root
sudo shred -u /etc/ssh/*_key /etc/ssh/*_key.pub
shred -u ~/.*history
shred -u ~/.gitconfig
