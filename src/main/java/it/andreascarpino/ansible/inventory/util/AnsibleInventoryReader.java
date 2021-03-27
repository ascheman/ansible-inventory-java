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
package it.andreascarpino.ansible.inventory.util;

import it.andreascarpino.ansible.inventory.type.AnsibleGroup;
import it.andreascarpino.ansible.inventory.type.AnsibleHost;
import it.andreascarpino.ansible.inventory.type.AnsibleInventory;
import it.andreascarpino.ansible.inventory.type.AnsibleVariable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.StringTokenizer;
import java.util.stream.Stream;

/**
 * @author Andrea Scarpino
 */
public class AnsibleInventoryReader {

	private AnsibleInventoryReader() {
	}

	protected static class AnsibleInventoryFactory {
		final AnsibleInventory inventory = new AnsibleInventory();
		// "all" is the default group which is always present and contains all hosts,
		// cf. https://docs.ansible.com/ansible/latest/user_guide/intro_inventory.html#default-groups
		AnsibleGroup all = new AnsibleGroup("all");
		// "ungrouped" is the default group which is always present and contains hosts which do not belong to any
		//other group, cf. https://docs.ansible.com/ansible/latest/user_guide/intro_inventory.html#default-groups
		AnsibleGroup ungrouped = new AnsibleGroup("ungrouped");

		protected AnsibleInventoryFactory() {
			inventory.addGroup(all);
			inventory.addGroup(ungrouped);
		}

		AnsibleGroup group = null;
		AnsibleHost host = null;
		boolean isVarsBlock = false;
		boolean isChildrenBlock = false;

		protected AnsibleInventory of (final String text) {
			final StringTokenizer tokenizer = new StringTokenizer(text, " \t\n\r\f", true);

			boolean skipComment = false;
			while (tokenizer.hasMoreTokens()) {
				final String token = tokenizer.nextToken();

				// New line, reset the comment flag
				if (isNewlineToken(token)) {
					skipComment = false;
					continue;
				}

				// We are still reading a comment line
				if (skipComment) {
					continue;
				}

				// Ignore separators
				if (isSeparatorToken(token)) {
					continue;
				}

				// We are reading a comment
				if (isCommentToken(token)) {
					skipComment = true;
					continue;
				}

				if (isGroupStartToken(token)) {
					createNewAnsibleGroup(token);
				} else if (isVariableToken(token)) {
					addVariable(token);
				} else {
					addHost(token);
				}
			}

			return inventory;
		}

		private boolean isNewlineToken(String token) {
			return "\n".equals(token);
		}

		private boolean isSeparatorToken(String token) {
			return " ".equals(token) || "\t".equals(token) || "\r".equals(token) || "\f".equals(token);
		}

		private boolean isCommentToken(String token) {
			return token.startsWith(";") || token.startsWith("#");
		}

		private boolean isGroupStartToken(String token) {
			return token.startsWith("[");
		}

		private void createNewAnsibleGroup(String token) {
			host = null;
			isChildrenBlock = false;
			isVarsBlock = false;

			String groupName = token.replaceAll("^\\[", "").replaceAll("]$", "");

			if (groupName.contains(":")) {
				final String[] g = groupName.split(":");

				groupName = g[0];

				if ("vars".equals(g[1])) {
					isVarsBlock = true;
					group = inventory.getGroup(groupName);
				} else if ("children".equals(g[1])) {
					isChildrenBlock = true;
					group = new AnsibleGroup(groupName);
					inventory.addGroup(group);
				}
			} else {
				group = new AnsibleGroup(groupName);
				inventory.addGroup(group);
			}
		}

		private boolean isVariableToken(String token) {
			return token.contains("=");
		}

		private void addVariable(String token) {
			final String[] v = token.split("=");
			// Replace YAML backslashes escapes
			final AnsibleVariable variable = new AnsibleVariable(v[0], v[1].replace("\\\\", "\\"));

			if (host != null) {
				host.addVariable(variable);
			} else if (isVarsBlock && group != null) {
				group.addVariable(variable);
				for (AnsibleGroup s : group.getSubgroups()) {
					for (AnsibleHost h : s.getHosts()) {
						h.addVariable(variable);
					}
				}
				for (AnsibleHost h : group.getHosts()) {
					h.addVariable(variable);
				}
			}
		}

		private void addHost(String token) {
			if (group == null) {
				host = new AnsibleHost(token);
				inventory.addHost(host);
				all.addHost(host);
				ungrouped.addHost(host);
			} else if (isChildrenBlock) {
				final AnsibleGroup g = inventory.getGroup(token);
				if (g != null) {
					group.addSubgroup(g);
				} else {
					group.addSubgroup(new AnsibleGroup(token));
				}
			} else {
				host = new AnsibleHost(token);
				inventory.addHost(host);
				all.addHost(host);
				group.addHost(host);
			}
		}
	}

	protected AnsibleInventory of (final String text) {
		return new AnsibleInventoryFactory().of(text);
	}

	public static AnsibleInventory read(String text) {
		return new AnsibleInventoryReader().of(text);
	}

	public static AnsibleInventory read(final Path inventoryPath) throws IOException {
		StringBuilder contentBuilder = new StringBuilder();
		try (Stream<String> stream = Files.lines(inventoryPath, StandardCharsets.UTF_8)) {
			stream.forEach(s -> contentBuilder.append(s).append("\n"));
		}

		return read(contentBuilder.toString());
	}
}
