---
coordinates: 32.8,-96.8
mac_addr: 00:16:3e:bd:4a:8c
ipv4_net: 192.138.210.113/23
ipv6_net: 2604:880:52:a9f::1/48
ipv6_gw: 2604:880:52::1

netplan_ethernets: |
  ens3:
    match:
      macaddress: 00:16:3e:bd:4a:8c
    set-name: ens3
    dhcp4: yes
    addresses:
      - 2604:880:52:a9f::1/48
    gateway6: 2604:880:52::1
