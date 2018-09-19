# [Ansible](https://ansible.com) playbooks for dlang CI servers

### Ansible Installation

- https://docs.ansible.com/ansible/intro_installation.html
- version should be >=2.2.0.0

### Setup

- ansible-galaxy install -r requirements.yml

- If your local username does not match your ssh account name on the
  server, configure that account name in your `~/.ssh/config`
  ```
  Host bla.dlang.org
      User accountname
  ```

- Each container runs it's own sshd server which can be accessed by proxing through the host.
  The following setting in your `~/.ssh/config` is recommended so that you can e.g. run `ssh root@jenkins.ci.lxd`.
  ```
  Host *.ci.lxd
     ProxyCommand ssh -W %h:%p ci.dlang.io
  ```

### Passwords

For less typing store vault, sudo, and profitbricks passwords in [Pass: The Standard Unix Password Manager](https://www.passwordstore.org/).
```sh
pass add dlangci/ansible_vault
pass add dlangci/sudo
```
At best also tell git how to diff encrypted files.
```sh
git config diff.ansible-vault.textconv 'ansible-vault view --vault-password-file=ansible/query_vault_pass.sh'
```
Alternatively comment out `vault_password_file` in [ansible.cfg](ansible.cfg) and `ansible_become_pass` in [group_vars/all](group_vars/all)
and pass `-K, --ask-become-pass` and `--ask-vault-pass` to `ansible-playbook`.

### Caches

- remove host facts caches under .cache/ in case major properties (e.g. IP address, hostname) change

### Prepare New Servers

- `ssh-copy-id eu.code.dlang.io`
- `ssh-copy-id na.code.dlang.io`
- `ssh-copy-id as.code.dlang.io`
- `ansible -m raw -a 'apt-get update -q=2' dub_hosts`
- `ansible -m raw -a 'apt-get install --no-install-recommends python-minimal python-apt -q=2' dub_hosts`
- `ansible -m ping dub_hosts`

## [Vagrant](https://www.vagrantup.com/) to setup local VirtualBox (Jenkins on http://172.16.1.2)

- [Download - Vagrant](https://www.vagrantup.com/downloads.html)

- see comment in [Vagrantfile](Vagrantfile) for an example of system-wide `~/.vagrant.d/Vagrantfile` VirtualBox defaults

- vagrant commands
  - vagrant up ci
  - vagrant halt ci
  - vagrant destroy ci
  - vagrant provision ci # rerun ansible
