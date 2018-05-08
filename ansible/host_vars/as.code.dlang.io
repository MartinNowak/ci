---
coordinates: 1.3,103.9
netplan_ethernets: |
  ens3:
    match:
      macaddress: fa:16:3e:20:1e:3e
    set-name: ens3
    dhcp4: yes
    addresses:
      - 2402:1f00:8000:800::d52/64
    gateway6: 2402:1f00:8000:800::1
