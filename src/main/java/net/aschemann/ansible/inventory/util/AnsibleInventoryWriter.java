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
package net.aschemann.ansible.inventory.util;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;

import net.aschemann.ansible.inventory.type.AnsibleGroup;
import net.aschemann.ansible.inventory.type.AnsibleHost;
import net.aschemann.ansible.inventory.type.AnsibleInventory;
import net.aschemann.ansible.inventory.type.AnsibleVariable;

/**
 * @author Andrea Scarpino
 */
public class AnsibleInventoryWriter {

	private AnsibleInventoryWriter() {
	}

	private static String groupHeader(String group) {
		return "[" + group + "]\n";
	}

	private static String variableBlock(AnsibleVariable variable) {
		final String val = variable.getValue().toString();

		// Escape backslashes for YAML
		return variable.getName() + "=" + val.replace("\\", "\\\\");
	}

	private static String groupVarsHeader(String group) {
		return "[" + group + ":vars]\n";
	}

	private static String groupOfGroupHeader(String group) {
		return "[" + group + ":children]\n";
	}

	private static String printHost(AnsibleHost host) {
		final StringBuilder builder = new StringBuilder();
		builder.append(host.getName());

		for (AnsibleVariable variable : host.getVariables()) {
			builder.append(" " + variableBlock(variable));
		}

		builder.append("\n");

		return builder.toString();
	}

	private static void printHost(AnsibleHost host, OutputStream stream) throws IOException {
		stream.write(host.getName().getBytes());

		for (AnsibleVariable variable : host.getVariables()) {
			stream.write((" " + variableBlock(variable)).getBytes());
		}

		stream.write("\n".getBytes());
	}

	public static String write(AnsibleInventory inventory) {
		final StringBuilder builder = new StringBuilder();

		appendHosts(builder, inventory.getHosts());

		appendGroups(builder, inventory);

		return builder.toString();
	}

	private static void appendGroups(StringBuilder builder, final AnsibleInventory inventory) {
		for (AnsibleGroup group : inventory.getGroups()) {
			appendGroup(builder, group);

			if (!group.getHosts().isEmpty()) {
				builder.append(groupHeader(group.getName()));

				appendHosts(builder, group.getHosts());
			}

			appendVariables(builder, group);
		}
	}

	private static void appendVariables(StringBuilder builder, final AnsibleGroup group) {
		if (!group.getVariables().isEmpty()) {
			builder.append(groupVarsHeader(group.getName()));

			if (!group.getVariables().isEmpty()) {
				for (AnsibleVariable variable : group.getVariables()) {
					builder.append(variableBlock(variable) + "\n");
				}
			} else {
				builder.append("\n");
			}
		}
	}

	private static void appendGroup(StringBuilder builder, final AnsibleGroup group) {
		if (!group.getSubgroups().isEmpty()) {
			builder.append(groupOfGroupHeader(group.getName()));

			if (!group.getSubgroups().isEmpty()) {
				for (AnsibleGroup g : group.getSubgroups()) {
					builder.append(g.getName() + "\n");
				}
			} else {
				builder.append("\n");
			}
		}
	}

	private static void appendHosts(StringBuilder builder, final Collection<AnsibleHost> hosts) {
		for (AnsibleHost host : hosts) {
			builder.append(printHost(host));
		}
	}

	public static void write(AnsibleInventory inventory, OutputStream stream) throws IOException {
		appendHosts(stream, inventory.getHosts());

		appendGroups(stream, inventory);
	}

	private static void appendGroups(OutputStream stream, final AnsibleInventory inventory) throws IOException {
		for (AnsibleGroup group : inventory.getGroups()) {
			appendSubgroups(stream, group);

			if (!group.getHosts().isEmpty()) {
				stream.write(groupHeader(group.getName()).getBytes());

				appendHosts(stream, group.getHosts());
			}

			appendVariables(stream, group);
		}
	}

	private static void appendVariables(OutputStream stream, final AnsibleGroup group) throws IOException {
		if (!group.getVariables().isEmpty()) {
			stream.write("\n".getBytes());
			stream.write(groupVarsHeader(group.getName()).getBytes());

			if (!group.getVariables().isEmpty()) {
				for (AnsibleVariable variable : group.getVariables()) {
					stream.write((variableBlock(variable) + "\n").getBytes());
				}
			} else {
				stream.write("\n".getBytes());
			}
		}
	}

	private static void appendSubgroups(OutputStream stream, final AnsibleGroup group) throws IOException {
		if (!group.getSubgroups().isEmpty()) {
			stream.write(groupOfGroupHeader(group.getName()).getBytes());

			if (!group.getSubgroups().isEmpty()) {
				for (AnsibleGroup g : group.getSubgroups()) {
					stream.write((g.getName() + "\n").getBytes());
				}
			} else {
				stream.write("\n".getBytes());
			}
		}
	}

	private static void appendHosts(OutputStream stream, Collection<AnsibleHost> hosts) throws IOException {
		for (AnsibleHost host : hosts) {
			printHost(host, stream);
		}
	}

}
