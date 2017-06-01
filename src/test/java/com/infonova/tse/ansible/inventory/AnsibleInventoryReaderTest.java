/*
 * The MIT License (MIT)
 * Copyright (c) 2016 Andrea Scarpino <me@andreascarpino.it>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the
 * Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.infonova.tse.ansible.inventory;

import com.infonova.tse.ansible.inventory.type.AnsibleGroup;
import com.infonova.tse.ansible.inventory.type.AnsibleHost;
import com.infonova.tse.ansible.inventory.type.AnsibleInventory;
import com.infonova.tse.ansible.inventory.type.AnsibleVariable;
import com.infonova.tse.ansible.inventory.util.AnsibleInventoryReader;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Andrea Scarpino
 */
public class AnsibleInventoryReaderTest {

	@Test
	public void testReadSimple() {
		String inventoryText = "[group1]\nhost1 var1=value1\n";

		AnsibleInventory inventory = AnsibleInventoryReader.read(inventoryText);

		Assert.assertEquals(1, inventory.getGroups().size());
		AnsibleGroup group = inventory.getGroups().iterator().next();
		Assert.assertEquals("group1", group.getName());
		Assert.assertEquals(1, group.getHosts().size());

		AnsibleHost host = group.getHosts().iterator().next();
		Assert.assertEquals("host1", host.getName());
		Assert.assertEquals(1, host.getVariables().size());

		AnsibleVariable variable = host.getVariables().iterator().next();
		Assert.assertEquals("var1", variable.getName());
		Assert.assertEquals("value1", variable.getValue());

		inventoryText = "[group1]\nhost1 var1=value1 var2=value2 var3=value3\nhost2\nhost3 var1=value1";

		inventory = AnsibleInventoryReader.read(inventoryText);
		group = inventory.getGroups().iterator().next();

		Assert.assertEquals(1, inventory.getGroups().size());
		Assert.assertEquals(3, group.getHosts().size());

		for (AnsibleHost h : group.getHosts()) {
			switch (h.getName()) {
			case "host1":
				Assert.assertEquals(3, h.getVariables().size());
				break;
			case "host2":
				Assert.assertEquals(0, h.getVariables().size());
				break;
			case "host3":
				Assert.assertEquals(1, h.getVariables().size());
				break;
			}
		}

		inventoryText = "host1 var1=value1";

		inventory = AnsibleInventoryReader.read(inventoryText);

		Assert.assertEquals(0, inventory.getGroups().size());
		Assert.assertEquals(1, inventory.getHosts().size());
		Assert.assertEquals(1, inventory.getHosts().iterator().next().getVariables().size());
	}

	@Test
	public void testReadNoGroup() {
		final String inventoryText = "host1 var1=value1\n";

		AnsibleInventory inventory = AnsibleInventoryReader.read(inventoryText);

		Assert.assertEquals(0, inventory.getGroups().size());
	}

	@Test
	public void testReadSkipComments() {
		final String inventoryText = ";I'm a comment\nhost1 var1=value1\n";

		AnsibleInventory inventory = AnsibleInventoryReader.read(inventoryText);

		Assert.assertEquals(0, inventory.getGroups().size());
	}

	@Test
	public void testReadNoHosts() {
		final String inventoryText = "[group1]\n";

		AnsibleInventory inventory = AnsibleInventoryReader.read(inventoryText);

		Assert.assertEquals(1, inventory.getGroups().size());
		Assert.assertEquals(0, inventory.getGroups().iterator().next().getHosts().size());
	}

	@Test
	public void testReadSubgroup() {
		final String inventoryText = "[subgroup1]\nhost1\n[subgroup2]\nhost2\n[group1:children]\nsubgroup1\nsubgroup2\n";

		AnsibleInventory inventory = AnsibleInventoryReader.read(inventoryText);

		Assert.assertEquals(3, inventory.getGroups().size());

		for (AnsibleGroup group : inventory.getGroups()) {
			if (group.getName().equals("group1")) {
				Assert.assertEquals(2, group.getSubgroups().size());
			}
		}
	}

	@Test
	public void testReadGroupVarsWithTrailingLineBreak() {
		testReadGroupVars("[subgroup1]\nhost1\n[subgroup2]\nhost2\n[group1:children]\nsubgroup1\nsubgroup2\n[group1:vars]\nvar1=value1\n");
	}
	@Test
	public void testReadGroupVarsWithOutTrailingLineBreak() {
		testReadGroupVars("[subgroup1]\nhost1\n[subgroup2]\nhost2\n[group1:children]\nsubgroup1\nsubgroup2\n[group1:vars]\nvar1=value1");
	}


	private void testReadGroupVars(final String inventoryText) {
		AnsibleInventory inventory = AnsibleInventoryReader.read(inventoryText);

		Assert.assertEquals(3, inventory.getGroups().size());

		for (AnsibleGroup group : inventory.getGroups()) {
			if (group.getName().equals("group1")) {
				Assert.assertEquals("var1", group.getSubgroups().iterator().next().getHosts().iterator().next()
						.getVariables().iterator().next().getName());
				Assert.assertEquals("value1", group.getSubgroups().iterator().next().getHosts().iterator().next()
						.getVariables().iterator().next().getValue());
			}
		}

		Assert.assertEquals("value1", inventory.getGroup("group1").getVariable("var1").getValue());
	}

	@Test
	public void testReadGroupVarsWithWindowsLineBreaks() {
		final String inventoryText = "[subgroup1]\r\nhost1\r\n[subgroup2]\r\nhost2\r\n[group1:children]\r\nsubgroup1\r\nsubgroup2\r\n[group1:vars]\r\nvar1=value1\r\n";

		AnsibleInventory inventory = AnsibleInventoryReader.read(inventoryText);

		Assert.assertEquals(3, inventory.getGroups().size());

		for (AnsibleGroup group : inventory.getGroups()) {
			if (group.getName().equals("group1")) {
				Assert.assertEquals("var1", group.getSubgroups().iterator().next().getHosts().iterator().next()
						.getVariables().iterator().next().getName());
				Assert.assertEquals("value1", group.getSubgroups().iterator().next().getHosts().iterator().next()
						.getVariables().iterator().next().getValue());
			}
		}

		Assert.assertEquals("value1", inventory.getGroup("group1").getVariable("var1").getValue());
	}


	@Test
	public void testReadAnsibleExample() {
		final String inventoryText = "[atlanta]\nhost1\nhost2\n\n[raleigh]\nhost2\nhost3\n\n[southeast:children]\n"
				+ "atlanta\nraleigh\n\n[southeast:vars]\nsome_server=foo.southeast.example.com\nhalon_system_timeout=30"
				+ "\nself_destruct_countdown=60\nescape_pods=2\n\n[usa:children]\nsoutheast\nnortheast\nsouthwest\n"
				+ "northwest\n";

		AnsibleInventory inventory = AnsibleInventoryReader.read(inventoryText);

		Assert.assertEquals(4, inventory.getGroups().size());

		for (AnsibleGroup group : inventory.getGroups()) {
			if (group.getName().equals("southeast")) {
				Assert.assertEquals(4,
						group.getSubgroups().iterator().next().getHosts().iterator().next().getVariables().size());
			}
		}
	}

	@Test
	public void testReadVarWithWhitespaces() {
		final String inventoryText =    "[test]\n" +
                                        "host1 host1var1=\"hostval 1\" host1var2='enclosed by single quotes'\n" +
                                        "host2 host2var1=\"this = a test\" host2var2=\"yet another[0] test!\"\n" +
                                        "[test:vars]\n" +
                                        "var1 = val1\n" +
                                        "var2 = \"this = a test\"\n" +
                                        "var3 = 'enclosed by single quotes'\n" +
                                        "var4=no quotes at all\n" +
                                        "var6 = this = also possible =\n" +
                                        "var5 = no quotes no linebreak (end of file)";

		AnsibleInventory inventory = AnsibleInventoryReader.read(inventoryText);

		Assert.assertEquals("val1", inventory.getGroup("test").getVariable("var1").getValue());
		Assert.assertEquals("\"this = a test\"", inventory.getGroup("test").getVariable("var2").getValue());
		Assert.assertEquals("\"hostval 1\"", inventory.getGroup("test").getHost("host1").getVariable("host1var1").getValue());
		Assert.assertEquals("\"this = a test\"", inventory.getGroup("test").getHost("host2").getVariable("host2var1").getValue());
		Assert.assertEquals("\"yet another[0] test!\"", inventory.getGroup("test").getHost("host2").getVariable("host2var2").getValue());

		// Single quotes
        Assert.assertEquals("'enclosed by single quotes'", inventory.getGroup("test").getHost("host1").getVariable("host1var2").getValue());
        Assert.assertEquals("'enclosed by single quotes'", inventory.getGroup("test").getVariable("var3").getValue());

        // Whitespace values in var section without double/sinle quotes
        Assert.assertEquals("no quotes at all", inventory.getGroup("test").getVariable("var4").getValue());
        Assert.assertEquals("no quotes no linebreak (end of file)", inventory.getGroup("test").getVariable("var5").getValue());

        // We also support strange things like that (as Ansbile also allows it)
		Assert.assertEquals("this = also possible =", inventory.getGroup("test").getVariable("var6").getValue());
	}

}
