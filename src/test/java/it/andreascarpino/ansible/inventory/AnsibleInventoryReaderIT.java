package it.andreascarpino.ansible.inventory;

import it.andreascarpino.ansible.inventory.type.AnsibleInventory;
import it.andreascarpino.ansible.inventory.util.AnsibleInventoryReader;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Paths;

public class AnsibleInventoryReaderIT {

    public static final String DIGITALOCEAN_INVENTORY_PATH = "src/test/resources/inventories/digitalocean-inventory";
    public static final String KUBERNETES_INVENTORY_PATH = "src/test/resources/inventories/kubernetes-inventory";
    public static final String VAGRANT_INVENTORY_PATH = "src/test/resources/inventories/vagrant-inventory";

    @Test
    public void readVagrantInventoryFile() throws IOException {
        AnsibleInventory ansibleInventory =
                AnsibleInventoryReader.read(Paths.get(VAGRANT_INVENTORY_PATH).toAbsolutePath());
        Assert.assertEquals(7, ansibleInventory.getHosts().size());
        Assert.assertEquals(1, ansibleInventory.getGroup("ungrouped").getHosts().size());
        Assert.assertEquals(7, ansibleInventory.getGroup("all").getHosts().size());
        Assert.assertEquals(2, ansibleInventory.getGroup("lamp_www").getHosts().size());
        Assert.assertEquals(1, ansibleInventory.getGroup("lamp_memcached").getHosts().size());
        Assert.assertEquals("192.168.2.2", ansibleInventory.getGroup("lamp_varnish").getHost("192.168.2.2").getName());
        Assert.assertEquals("slave",
                ansibleInventory
                        .getGroup("lamp_db")
                        .getHost("192.168.2.6")
                        .getVariable("mysql_replication_role").getValue());
    }

    @Test
    public void readDigitaloceanInventoryFile() throws IOException {
        AnsibleInventory ansibleInventory =
                AnsibleInventoryReader.read(Paths.get(DIGITALOCEAN_INVENTORY_PATH).toAbsolutePath());
        Assert.assertEquals(0, ansibleInventory.getHosts().size());
        Assert.assertEquals(2, ansibleInventory.getGroup("lamp_www").getSubgroups().size());
    }

    @Test
    public void readKuberneteInventoryFile() throws IOException {
        AnsibleInventory ansibleInventory =
                AnsibleInventoryReader.read(Paths.get(KUBERNETES_INVENTORY_PATH).toAbsolutePath());
        Assert.assertEquals(3, ansibleInventory.getHosts().size());
        Assert.assertEquals(0, ansibleInventory.getGroup("ungrouped").getHosts().size());
        Assert.assertEquals(3, ansibleInventory.getGroup("all").getHosts().size());
        Assert.assertEquals(0, ansibleInventory.getGroup("k8s").getHosts().size());
        Assert.assertEquals(2, ansibleInventory.getGroup("k8s").getSubgroups().size());
        Assert.assertEquals(2, ansibleInventory.getGroup("k8s_nodes").getHosts().size());
        Assert.assertEquals("master", ansibleInventory.getGroup("k8s_master").getHost("master").getName());
        Assert.assertEquals("vagrant",
                ansibleInventory
                        .getGroup("k8s")
                        .getVariable("ansible_user").getValue());
    }
}
