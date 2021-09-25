#!/usr/bin/env python3

from ansible.inventory.manager import InventoryManager
from ansible.parsing.dataloader import DataLoader

loader = DataLoader()

def debug(msg):
    print("%s" % msg)


debug("loading inventory")
inventory = InventoryManager(sources='src/test/resources/inventories/directories/vagrant-inventory', loader=loader)
hosts = inventory.get_hosts()[:]

lamp_db_group = inventory.groups["lamp_db"]
ansible_user = inventory.groups["lamp_db"].hosts[1].get_vars()["ansible_user"]
debug("done loading inventory")
