---
netplan_ethernets: |
  ens3:
    match:
      macaddress: fa:16:3e:20:1e:3e
    set-name: ens3
    dhcp4: yes
    addresses:
      - 2402:1f00:8000:800::d52/64
    gateway6: 2402:1f00:8000:800::1
    nameservers:
      search:
        - code.dlang.io
      addresses:
        - 1.1.1.1
        - 8.8.8.8
        - 2606:4700:4700::1001
