---
coordinates: 50.5,12.3
netplan_ethernets: |
  ens3:
    match:
      macaddress: 96:00:00:0a:4c:76
    set-name: ens3
    dhcp4: yes
    addresses:
      - 2a01:4f8:1c17:61c2::1/64
    gateway6: fe80::1
