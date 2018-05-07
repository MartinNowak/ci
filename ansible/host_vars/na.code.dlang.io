---
netplan_ethernets: |
  ens3:
    match:
      macaddress: 00:16:3e:bd:4a:8c
    set-name: ens3
    dhcp4: yes
    addresses:
      - 2604:880:52:a9f::1/48
    gateway6: 2604:880:52::1
    nameservers:
      search:
        - code.dlang.io
      addresses:
        - 1.1.1.1
        - 8.8.8.8
        - 2606:4700:4700::1001
