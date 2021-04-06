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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Andrea Scarpino
 */
public class AnsibleInventoryReader {

	private AnsibleInventoryReader() {
	}

	protected static class AnsibleInventoryFactory {
		private static final String DELIMITERS = " \t\r\f";

		final AnsibleInventory inventory = new AnsibleInventory();
		// "all" is the default group which is always present and contains all hosts,
		// cf. https://docs.ansible.com/ansible/latest/user_guide/intro_inventory.html#default-groups
		private final AnsibleGroup all = new AnsibleGroup("all");
		// "ungrouped" is the default group which is always present and contains hosts which do not belong to any
		//other group, cf. https://docs.ansible.com/ansible/latest/user_guide/intro_inventory.html#default-groups
		private final AnsibleGroup ungrouped = new AnsibleGroup("ungrouped");

		protected AnsibleInventoryFactory() {
			inventory.addGroup(all);
			inventory.addGroup(ungrouped);
		}

		final Map<String, List<String>> groupBlocks = new HashMap<>();
		final Map<String, List<String>> varBlocks = new HashMap<>();
		final Map<String, List<String>> childrenBlocks = new HashMap<>();
		List<String> currentLines = new ArrayList<>();
		String currentName = "ungrouped";
		boolean isGroupBlock = true;
		boolean isVarsBlock = false;
		boolean isChildrenBlock = false;

		private void finishCurrentLines(final String line) {
			if (isVarsBlock) {
				varBlocks.put(currentName, currentLines);
			} else if (isChildrenBlock) {
				childrenBlocks.put(currentName, currentLines);
			} else if (isGroupBlock) {
				groupBlocks.put(currentName, currentLines);
			} else {
				throw new IllegalStateException("Unclear State: this should never happen!");
			}
			currentName = line;
			currentLines = new LinkedList<>();
			isVarsBlock = false;
			isChildrenBlock = false;
			isGroupBlock = false;
		}

		private void startNewGroupVars(final String line) {
			finishCurrentLines(line);
			isVarsBlock = true;
		}

		private void startNewGroupChildren(final String line) {
			finishCurrentLines(line);
			isChildrenBlock = true;
		}

		private void startNewGroup(final String line) {
			int closingParenPos = line.indexOf(']');
			String groupName = line.substring(1, closingParenPos);
			finishCurrentLines(groupName);
			isGroupBlock = true;
		}

		protected AnsibleInventory of(final List<String> lines) {
			lines.forEach(line -> {
				String normalizedLine = getNormalizedText(line);
				if (isGroupVarsStartToken(normalizedLine)) {
					startNewGroupVars(normalizedLine);
				} else if (isGroupChildrenStartToken(normalizedLine)) {
					startNewGroupChildren(normalizedLine);
				} else if (isGroupStartToken(normalizedLine)) {
					startNewGroup(normalizedLine);
				} else if (!isCommentToken(normalizedLine) && !normalizedLine.isEmpty()) {
					currentLines.add(normalizedLine);
				}
			});
			finishCurrentLines(null);

			groupBlocks.forEach((name, listOfHostnamesWithVars) -> {
				AnsibleGroup currentGroup = getOrAddGroup(inventory, name);
				listOfHostnamesWithVars.forEach(line -> {
					final String[] hostNameAndVars = line.split("[ \t]", 2);
					AnsibleHost currentHost = getOrAddHost(inventory, hostNameAndVars[0]);
					currentGroup.addHost(currentHost);
					if (hostNameAndVars.length > 1) {
						addVariables(currentHost, hostNameAndVars[1]);
					}
				});
			});

			childrenBlocks.forEach((name, listOfChildrenNames) -> {
				AnsibleGroup currentGroup = getGroup(name);
				listOfChildrenNames.forEach(childGroupName -> {
					final AnsibleGroup childGroup = inventory.getGroup(childGroupName);
					if (null != childGroup) {
						currentGroup.addSubgroup(childGroup);
					}
				});
			});

			varBlocks.forEach(this::mergeGroupVariables);

			return inventory;
		}

		public void mergeGroupVariables(final String name, final List<String> listOfVariables) {
			AnsibleGroup currentGroup = getGroup(name);
			listOfVariables.forEach(line -> addVariable(line, null, currentGroup));
		}

		private AnsibleGroup getGroup(final String name) {
			int colonPosition = name.indexOf(':');
			String groupName = name.substring(1, colonPosition);
			return getOrAddGroup(inventory, groupName);
		}

		private void addVariables(final AnsibleHost host, final String vars) {
			List<String> variables = splitVariables(vars, isVarsBlock);

			variables.forEach(token -> addVariable(token, host, null));
		}

		protected List<String> splitVariables(final String vars, final boolean isVarsBlock) {
			final StringTokenizer tokenizer = new StringTokenizer(vars, DELIMITERS, true);

			StringBuilder tokenBuilder = null; // we need this "temp token" for whitespace values
			boolean isValueWithWhitespace = false;
			String quoteSign = "";

			List<String> variables = new LinkedList<>();
			while (tokenizer.hasMoreTokens()) {
				final String token = tokenizer.nextToken();

				if (!isValueWithWhitespace) {
					tokenBuilder = null; // reset the tmpToken
				}

				if (tokenBuilder == null) {
					// check for whitespace values enclosed by double quotes
					if (token.matches(".*?=\\s*\".*")) {
						tokenBuilder = new StringBuilder(token);
						quoteSign = "\"";
					}
					// check for whitespace values enclosed by single quotes
					else if (token.matches(".*?=\\s*'.*")) {
						tokenBuilder = new StringBuilder(token);
						quoteSign = "'";

					}
					// in a vars block no quotes are required
					else if (token.matches("\\S*=\\s*.*") && isVarsBlock) {
						tokenBuilder = new StringBuilder(token);
						quoteSign = "\n";
					}

					if (tokenBuilder != null && !token.endsWith(quoteSign) && tokenizer.hasMoreTokens()) {
						isValueWithWhitespace = true;
						continue;
					}
				}

				// Have we reached the end of a value containing whitespace? (Or, are we at the end of the file?)
				if (isValueWithWhitespace && (token.endsWith(quoteSign) || !tokenizer.hasMoreTokens())) {
					if (!"\n".equals(token)) {
						tokenBuilder.append(token);
					}
					isValueWithWhitespace = false;
				}

				if (isValueWithWhitespace) {
					// Append the token to tmpToken
					if (!"\r".equals(token)) {
						tokenBuilder.append(token);
					}
					continue;
				} else {
					// Otherwise, assign token to tmpToken
					if (tokenBuilder == null) {
						tokenBuilder = new StringBuilder(token);
					}
				}

				// Ignore separators
				if (isSeparatorToken(token)) {
					continue;
				}

				variables.add(tokenBuilder.toString());
			}
			return variables;
		}

		private String getNormalizedText(final String text) {
			// Convert "foo = bar" to "foo=bar" (as Ansible allows to use that format but it would cause problems here)
			Pattern p = Pattern.compile("^(\\S*)\\s*=\\s*(.*)$", Pattern.MULTILINE);
			Matcher m = p.matcher(text);
			return m.replaceAll("$1=$2");
		}

		private boolean isSeparatorToken(final String token) {
			return token.matches("[" + DELIMITERS + "]");
		}

		private boolean isCommentToken(final String token) {
			return token.startsWith(";") || token.startsWith("#");
		}

		private boolean isGroupStartToken(final String token) {
			return token.startsWith("[");
		}

		private boolean isGroupVarsStartToken(final String token) {
			return token.matches("^\\[\\w+:vars]$");
		}

		private boolean isGroupChildrenStartToken(final String token) {
			return token.matches("^\\[\\w+:children]$");
		}

		private static AnsibleGroup getOrAddGroup(final AnsibleInventory inventory, final String groupName) {
			AnsibleGroup group = inventory.getGroup(groupName);
			if (group == null) {
				group = new AnsibleGroup(groupName);
				inventory.addGroup(group);
			}
			return group;
		}

		private AnsibleHost getOrAddHost(final AnsibleInventory inventory, final String hostName) {
			AnsibleHost currentHost = inventory.getHost(hostName);
			if (currentHost == null) {
				currentHost = new AnsibleHost(hostName);
				inventory.addHost(currentHost);
				all.addHost(currentHost);
			}
			return currentHost;
		}

		private void addVariable(final String token, final AnsibleHost host, final AnsibleGroup group) {
			final String[] v = token.split("=", 2);
			// Replace YAML backslashes escapes
			final AnsibleVariable variable = new AnsibleVariable(v[0], v[1].replace("\\\\", "\\"));

			if (host != null) {
				host.addVariable(variable);
			}

			if (group != null) {
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
			}
		}
	}

	public static AnsibleInventory read(final String text) {
		List<String> textAsList = Arrays.asList(text.split("\\n"));
		return read(textAsList);
	}

	public static AnsibleInventory read(final Path inventoryPath) throws IOException {
		List<String> inventoryAsList = Files.readAllLines(inventoryPath, StandardCharsets.UTF_8);
		return read(inventoryAsList);
	}

	public static AnsibleInventory read(final List<String> lines) {
		return new AnsibleInventoryFactory().of(lines);
	}
}
