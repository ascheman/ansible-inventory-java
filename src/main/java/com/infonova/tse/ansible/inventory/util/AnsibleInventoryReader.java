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
package com.infonova.tse.ansible.inventory.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.infonova.tse.ansible.inventory.type.AnsibleHost;
import com.infonova.tse.ansible.inventory.type.AnsibleInventory;
import com.infonova.tse.ansible.inventory.type.AnsibleVariable;
import com.infonova.tse.ansible.inventory.type.AnsibleGroup;

/**
 * @author Andrea Scarpino
 */
public class AnsibleInventoryReader {

	private AnsibleInventoryReader() {
	}

	public static AnsibleInventory read(File f) throws IOException {
		String s = new String(Files.readAllBytes(Paths.get(f.getPath())));
		return read(s);
	}

	public static AnsibleInventory read(String text) {
		final AnsibleInventory inventory = new AnsibleInventory();

		// Convert "foo = bar" to "foo=bar" (as Ansible allows to use that format but it would cause problems here)
		Pattern p = Pattern.compile("^(\\S*)\\s*=\\s*(.*)$", Pattern.MULTILINE);
		Matcher m = p.matcher(text);
		text = m.replaceAll("$1=$2");

		final StringTokenizer tokenizer = new StringTokenizer(text, " \t\n\r\f", true);

		AnsibleGroup group = null;
		AnsibleHost host = null;
		boolean skipComment = false;
		boolean isVarsBlock = false;
		boolean isChildrenBlock = false;
		String tmpToken = null; // we need this "temp token" for whitespace values
		boolean isValueWithWhitespace = false;
		String quoteSign = "";

		while (tokenizer.hasMoreTokens()) {
			final String token = tokenizer.nextToken();

			if(! isValueWithWhitespace) tmpToken = null; // reset the tmpToken

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
                    if (!tmpToken.endsWith(quoteSign)) isValueWithWhitespace = true;
                    continue;
                }

			}

			// Have we reached the end of a value containing whitespace? (Or, are we at the end of the file?)
			if (isValueWithWhitespace && (token.endsWith(quoteSign) || ! tokenizer.hasMoreTokens())) {
				if (! "\n".equals(token)) tmpToken += token;
				isValueWithWhitespace = false;
			}

			if (isValueWithWhitespace) {
			    // Append the token to tmpToken
				tmpToken += token;
				continue;
			} else {
			    // Otherwise, assign token to tmpToken
				if (tmpToken == null) {
					tmpToken = token;
				}
			}


			// New line, reset the comment flag
			if ("\n".equals(tmpToken)) {
				skipComment = false;
				continue;
			}

			// We are still reading a comment line
			if (skipComment) {
				continue;
			}

			// Ignore separators
			if (" ".equals(tmpToken) || "\t".equals(tmpToken) || "\r".equals(tmpToken) || "\f".equals(tmpToken)) {
				continue;
			}

			// We are reading a comment
			if (tmpToken.startsWith(";") || tmpToken.startsWith("#")) {
				skipComment = true;
				continue;
			}

			if (tmpToken.startsWith("[")) {
				host = null;
				isChildrenBlock = false;
				isVarsBlock = false;

				String groupName = tmpToken.replaceAll("^\\[", "").replaceAll("]$", "");

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
			} else if (tmpToken.contains("=")) {
				final String[] v = tmpToken.split("=", 2);

				// Replace YAML backslashes escapes
				final AnsibleVariable variable = new AnsibleVariable(v[0], v[1].replace("\\\\", "\\"));
				if (host != null) {
					host.addVariable(variable);
				}

				if (isVarsBlock && group != null) {
					for (AnsibleGroup s : group.getSubgroups()) {
						for (AnsibleHost h : s.getHosts()) {
							h.addVariable(variable);
						}

						if (s.getVariable(variable.getName()) == null) s.addVariable(variable);
					}
					for (AnsibleHost h : group.getHosts()) {
						h.addVariable(variable);
					}
					group.addVariable(variable);
				}
			} else {
				if (group == null) {
					host = new AnsibleHost(tmpToken);
					inventory.addHost(host);
				} else if (isChildrenBlock) {
					final AnsibleGroup g = inventory.getGroup(tmpToken);
					if (g != null) {
						group.addSubgroup(g);
					} else {
						group.addSubgroup(new AnsibleGroup(tmpToken));
					}
				} else {
					host = new AnsibleHost(tmpToken);
					group.addHost(host);
				}
			}
		}

		return inventory;
	}

}