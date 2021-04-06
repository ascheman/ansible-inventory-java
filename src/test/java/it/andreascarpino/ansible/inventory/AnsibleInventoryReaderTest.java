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
package it.andreascarpino.ansible.inventory;

import it.andreascarpino.ansible.inventory.type.AnsibleGroup;
import it.andreascarpino.ansible.inventory.type.AnsibleHost;
import it.andreascarpino.ansible.inventory.type.AnsibleInventory;
import it.andreascarpino.ansible.inventory.type.AnsibleVariable;
import it.andreascarpino.ansible.inventory.util.AnsibleInventoryReader;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * @author Andrea Scarpino
 */
public class AnsibleInventoryReaderTest {

	@Test
	public void testReadSimple() {
		List<String> inventoryList = Arrays.asList(new String[]{
				"[group1]",
				"host1 var1=value1"
		});

		AnsibleInventory inventory = AnsibleInventoryReader.read(inventoryList);

		Assert.assertEquals(3, inventory.getGroups().size());
		AnsibleGroup group = inventory.getGroups().iterator().next();
		Assert.assertEquals("all", group.getName());
		Assert.assertEquals(1, group.getHosts().size());

		AnsibleHost host = group.getHosts().iterator().next();
		Assert.assertEquals("host1", host.getName());
		Assert.assertEquals(1, host.getVariables().size());

		AnsibleVariable variable = host.getVariables().iterator().next();
		Assert.assertEquals("var1", variable.getName());
		Assert.assertEquals("value1", variable.getValue());

		inventoryList = Arrays.asList(new String[]{
				"[group1]",
				"host1 var1=value1 var2=value2 var3=value3",
				"host2",
				"host3 var1=value1"
		});

		inventory = AnsibleInventoryReader.read(inventoryList);
		group = inventory.getGroups().iterator().next();

		Assert.assertEquals(3, inventory.getGroups().size());
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

		inventoryList = Arrays.asList(new String[]{
				"host1 var1=value1"
		});

		inventory = AnsibleInventoryReader.read(inventoryList);

		Assert.assertEquals(2, inventory.getGroups().size());
		Assert.assertEquals(1, inventory.getHosts().size());
		Assert.assertEquals(1, inventory.getHosts().iterator().next().getVariables().size());
	}

	@Test
	public void testReadNoGroup() {
		final List<String> inventoryList = Arrays.asList(new String[]{"host1 var1=value1"});

		AnsibleInventory inventory = AnsibleInventoryReader.read(inventoryList);

		Assert.assertEquals(2, inventory.getGroups().size());
	}

	@Test
	public void testReadSkipComments() {
		final List<String> inventoryList = Arrays.asList(new String[]{
				";I'm a comment",
				"host1 var1=value1"
		});

		AnsibleInventory inventory = AnsibleInventoryReader.read(inventoryList);

		Assert.assertEquals(2, inventory.getGroups().size());
	}

	@Test
	public void testReadNoHosts() {
		final List<String> inventoryList = Arrays.asList(new String[]{"[group1]"});

		AnsibleInventory inventory = AnsibleInventoryReader.read(inventoryList);

		Assert.assertEquals(3, inventory.getGroups().size());
		Assert.assertEquals(0, inventory.getGroups().iterator().next().getHosts().size());
	}

	@Test
	public void testReadSubgroup() {
		final List<String> inventoryList = Arrays.asList(new String[]{
				"[subgroup1]",
				"host1",
				"[subgroup2]",
				"host2",
				"[group1:children]",
				"subgroup1",
				"subgroup2"
		});

		AnsibleInventory inventory = AnsibleInventoryReader.read(inventoryList);

		Assert.assertEquals(5, inventory.getGroups().size());

		for (AnsibleGroup group : inventory.getGroups()) {
			if (group.getName().equals("group1")) {
				Assert.assertEquals(2, group.getSubgroups().size());
			}
		}
	}

	@Test
	public void testReadGroupVars() {
		final List<String> inventoryList = Arrays.asList(new String[]{
				"[subgroup1]",
				"host1",
				"[subgroup2]",
				"host2",
				"[group1:children]",
				"subgroup1",
				"subgroup2",
				"",
				"[group1:vars]",
				"var1=value1"
		});

		AnsibleInventory inventory = AnsibleInventoryReader.read(inventoryList);

		Assert.assertEquals(5, inventory.getGroups().size());

		for (AnsibleGroup group : inventory.getGroups()) {
			if (group.getName().equals("group1")) {
				Assert.assertEquals("var1", group.getSubgroups().iterator().next().getHosts().iterator().next()
						.getVariables().iterator().next().getName());
				Assert.assertEquals("value1", group.getSubgroups().iterator().next().getHosts().iterator().next()
						.getVariables().iterator().next().getValue());
			}
		}
	}

	@Test
	public void testReadAnsibleExample() {
		final List<String> inventoryList = Arrays.asList(new String[]{
				"[atlanta]",
				"host1",
				"host2",
				"",
				"[raleigh]",
				"host2",
				"host3",
				"",
				"[southeast:children]",
				"",
				"atlanta",
				"raleigh",
				"",
				"[southeast:vars]",
				"some_server=foo.southeast.example.com",
				"halon_system_timeout=30",
				"",
				"self_destruct_countdown=60",
				"escape_pods=2",
				"",
				"[usa:children]",
				"southeast",
				"northeast",
				"southwest",
				"northwest"
		});

		AnsibleInventory inventory = AnsibleInventoryReader.read(inventoryList);

		Assert.assertEquals(6, inventory.getGroups().size());

		for (AnsibleGroup group : inventory.getGroups()) {
			if (group.getName().equals("southeast")) {
				Assert.assertEquals(4,
						group.getSubgroups().iterator().next().getHosts().iterator().next().getVariables().size());
			}
		}
	}

	@Test
	public void testReadVarWithWhitespaces() {
		final List<String> inventoryList = Arrays.asList(new String[]{
				"[test]",
				"host1 host1var1=\"hostval 1\" host1var2='enclosed by single quotes'",
				"host2 host2var1=\"this = a test\" host2var2=\"yet another[0] test!\"",
				"",
				"[test:vars]",
				"var1 = val1",
				"var2 = \"this = a test\"",
				"var3 = 'enclosed by single quotes'",
				"var4=no quotes at all",
				"var6 = this = also possible =",
				"var5 = no quotes no linebreak (end of file)"
		});

		AnsibleInventory inventory = AnsibleInventoryReader.read(inventoryList);

		Assert.assertEquals("val1", inventory.getGroup("test").getVariable("var1").getValue());
		Assert.assertEquals("\"this = a test\"", inventory.getGroup("test").getVariable("var2").getValue());
		Assert.assertEquals("\"hostval 1\"", inventory.getGroup("test").getHost("host1").getVariable(
				"host1var1").getValue());
		Assert.assertEquals("\"this = a test\"", inventory.getGroup("test").getHost("host2").getVariable(
				"host2var1").getValue());
		Assert.assertEquals("\"yet another[0] test!\"",
				inventory.getGroup("test").getHost("host2").getVariable("host2var2").getValue());

		// Single quotes
		Assert.assertEquals("'enclosed by single quotes'",
				inventory.getGroup("test").getHost("host1").getVariable("host1var2").getValue());
		Assert.assertEquals("'enclosed by single quotes'",
				inventory.getGroup("test").getVariable("var3").getValue());

		// Whitespace values in var section without double/sinle quotes
		Assert.assertEquals("no quotes at all", inventory.getGroup("test").getVariable("var4").getValue());
		Assert.assertEquals("no quotes no linebreak (end of file)",
				inventory.getGroup("test").getVariable("var5").getValue());

		// We also support strange things like that (as Ansbile also allows it)
		Assert.assertEquals("this = also possible =", inventory.getGroup("test").getVariable("var6").getValue());
	}

	@Test
	public void testReadGroupVarsWithTrailingLineBreak() {
		testReadGroupVars(Arrays.asList(new String[]{
				"[subgroup1]",
				"host1",
				"[subgroup2]",
				"host2",
				"[group1:children]",
				"subgroup1",
				"subgroup2",
				"[group1:vars]",
				"var1=value1"
		}));
	}

	@Test
	public void testReadGroupVarsWithOutTrailingLineBreak() {
		testReadGroupVars(Arrays.asList(new String[]{
				"[subgroup1]",
				"host1",
				"[subgroup2]",
				"host2",
				"[group1:children]",
				"subgroup1",
				"subgroup2",
				"[group1:vars]",
				"var1=value1"
		}));
	}


	private void testReadGroupVars(final List<String> inventoryList) {
		AnsibleInventory inventory = AnsibleInventoryReader.read(inventoryList);

		Assert.assertEquals(5, inventory.getGroups().size());

		for (AnsibleGroup group : inventory.getGroups()) {
			if (group.getName().equals("group1")) {
				Assert.assertEquals("var1", group.getSubgroups().iterator().next().getHosts().iterator().next()
						.getVariables().iterator().next().getName());
				Assert.assertEquals("value1",
						group.getSubgroups().iterator().next().getHosts().iterator().next()
								.getVariables().iterator().next().getValue());
			}
		}

		Assert.assertEquals("value1", inventory.getGroup("group1").getVariable("var1").getValue());
	}

	@Test
	public void testVarComments() {
		final List<String> inventoryList = Arrays.asList(new String[]{
				"[test]",
				"host1",
				"[test:vars]",
				"var1=val1",
				"#foo=bar",
				"var2 = #val2",
				";var3=commented out",
				"var4=val4"
		});
		AnsibleInventory inventory = AnsibleInventoryReader.read(inventoryList);

		Assert.assertEquals(null, inventory.getGroup("test").getVariable("foo"));
		Assert.assertEquals(null, inventory.getGroup("test").getVariable("#foo"));
		Assert.assertEquals("val1", inventory.getGroup("test").getVariable("var1").getValue());
		Assert.assertEquals("#val2", inventory.getGroup("test").getVariable("var2").getValue());
		Assert.assertEquals(null, inventory.getGroup("test").getVariable("var3"));
		Assert.assertEquals("val4", inventory.getGroup("test").getVariable("var4").getValue());

	}
}
