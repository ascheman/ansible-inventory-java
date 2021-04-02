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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
			// Convert "foo = bar" to "foo=bar" (as Ansible allows to use that format but it would cause problems here)
			Pattern p = Pattern.compile("^(\\S*)\\s*=\\s*(.*)$", Pattern.MULTILINE);
			Matcher m = p.matcher(text);
			String normalizedText = m.replaceAll("$1=$2");

			final StringTokenizer tokenizer = new StringTokenizer(normalizedText, " \t\n\r\f", true);

			boolean skipComment = false;
			String tmpToken = null; // we need this "temp token" for whitespace values
			boolean isValueWithWhitespace = false;
			String quoteSign = "";

			while (tokenizer.hasMoreTokens()) {
				final String token = tokenizer.nextToken();

				if (!isValueWithWhitespace) {
					tmpToken = null; // reset the tmpToken
				}

				if (tmpToken == null) {
					// check for whitespace values enclosed by double quotes
					if (token.matches(".*?=\\s*\".*")) {
						tmpToken = token;
						quoteSign = "\"";
					}
					// check for whitespace values enclosed by single quotes
					else if (token.matches(".*?=\\s*\'.*")) {
						tmpToken = token;
						quoteSign = "\'";

					}
					// in a vars block no quotes are required
					else if (token.matches("\\S*=\\s*.*") && isVarsBlock) {
						tmpToken = token;
						quoteSign = "\n";
					}

					if (tmpToken != null) {
						if (!tmpToken.endsWith(quoteSign) && tokenizer.hasMoreTokens()) {
							isValueWithWhitespace = true;
							continue;
						}
					}
				}

				// Have we reached the end of a value containing whitespace? (Or, are we at the end of the file?)
				if (isValueWithWhitespace && (token.endsWith(quoteSign) || !tokenizer.hasMoreTokens())) {
					if (!"\n".equals(token)) {
						tmpToken += token;
					}
					isValueWithWhitespace = false;
				}

				if (isValueWithWhitespace) {
					// Append the token to tmpToken
					if (! "\r".equals(token)) {
						tmpToken += token;
					}
					continue;
				} else {
					// Otherwise, assign token to tmpToken
					if (tmpToken == null) {
						tmpToken = token;
					}
				}

				// New line, reset the comment flag
				if (isNewlineToken(tmpToken)) {
					skipComment = false;
					continue;
				}

				// We are still reading a comment line
				if (skipComment) {
					continue;
				}

				// Ignore separators
				if (isSeparatorToken(tmpToken)) {
					continue;
				}

				// We are reading a comment
				if (isCommentToken(tmpToken)) {
					skipComment = true;
					continue;
				}

				if (isGroupStartToken(tmpToken)) {
					createNewAnsibleGroup(tmpToken);
				} else if (isVariableToken(tmpToken)) {
					addVariable(tmpToken);
				} else {
					addHost(tmpToken);
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
					group = getOrAddGroup(groupName, inventory);
				} else if ("children".equals(g[1])) {
					isChildrenBlock = true;
					group = getOrAddGroup(groupName, inventory);
				}
			} else {
				group = getOrAddGroup(groupName, inventory);
			}
		}

		private static AnsibleGroup getOrAddGroup(String groupName, AnsibleInventory inventory) {
			AnsibleGroup group = inventory.getGroup(groupName);
			if (group == null) {
				group = new AnsibleGroup(groupName);
				inventory.addGroup(group);
			}
			return group;
		}

		private boolean isVariableToken(String token) {
			return token.contains("=");
		}

		private void addVariable(String token) {
			final String[] v = token.split("=", 2);
			// Replace YAML backslashes escapes
			final AnsibleVariable variable = new AnsibleVariable(v[0], v[1].replace("\\\\", "\\"));

			if (host != null) {
				host.addVariable(variable);
			}

			if (isVarsBlock && group != null) {
				group.addVariable(variable);
				for (AnsibleGroup s : group.getSubgroups()) {
					for (AnsibleHost h : s.getHosts()) {
						h.addVariable(variable);
					}

					if (s.getVariable(variable.getName()) == null) {
						s.addVariable(variable);
					}
				}
				for (AnsibleHost h : group.getHosts()) {
					h.addVariable(variable);
				}
				group.addVariable(variable);
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
