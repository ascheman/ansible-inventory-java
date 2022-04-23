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
package net.aschemann.ansible.inventory.type;

import org.apache.commons.lang3.ClassUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

/**
 * @author Andrea Scarpino
 * @see AnsibleConstants
 */
public class AnsibleVariable {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnsibleVariable.class);

	private final String name;

	private Object value;

	public AnsibleVariable(String name) {
		super();
		this.name = name;
	}

	public AnsibleVariable(String name, Object value) {
		this(name);
		this.value = value;
	}

	public String getName() {
		return name;
	}

	public Object getValue() {
		return value;
	}

	public void setValue(Object value) {
		this.value = value;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = (prime * result) + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AnsibleVariable other = (AnsibleVariable) obj;
		if (name == null) {
            return other.name == null;
		} else return name.equals(other.name);
    }

	@Override
    public String toString() {
        if (this.value == null) {
            return "";
        }

        return this.name + "=" + valueToString(this.value);
    }

    public String valueToString(Object value) {
        if (value == null) {
            return "";
        }

        final Class<?> vClass = value.getClass();

        String str;
        if (Collection.class.isAssignableFrom(vClass)) {
            str = listToString((Collection<?>) value);
        } else if (Map.class.isAssignableFrom(vClass)) {
            str = mapToString((Map<?, ?>) value);
        } else if (ClassUtils.isPrimitiveOrWrapper(vClass) || value instanceof String || vClass.isEnum()) {
            str = value.toString();

            // Use double backslash because of YAML syntax
            str = str.replace("\\", "\\\\");

            // Escape quotes
            str = str.replace("\"", "\\\"");

            // Quote variables with spaces
            if (str.contains(" ")) {
              str = "\"" + str + "\"";
            }
        } else {
            str = objToString(value);
        }

        return str;
    }

    public String objToString(Object value) {
        final StringBuilder buf = new StringBuilder();

        for (Field f : value.getClass().getDeclaredFields()) {
            f.setAccessible(true);

            try {
                buf.append("\"").append(f.getName()).append("\": ");
                if (ClassUtils.isPrimitiveOrWrapper(value.getClass()) || value instanceof String) {
                    buf.append("\"").append(value).append("\"");
                } else {
                    buf.append(valueToString(f.get(value)));
                }
                buf.append(", ");
            } catch (IllegalArgumentException | IllegalAccessException e) {
                // Silently ignore errors
                LOGGER.warn("Could not map objToString for Field of class '{}'", f.getClass(), e);
            }
        }
        buf.replace(buf.length() - 2, buf.length(), "");

        return buf.toString();
    }

    public String listToString(Collection<?> list) {
        final StringBuilder buf = new StringBuilder();
        buf.append("'[");

        if (!list.isEmpty()) {
            for (Object o : list) {
                if (ClassUtils.isPrimitiveOrWrapper(o.getClass()) || o instanceof String) {
                    buf.append("\"").append(o).append("\"");
                } else {
                    buf.append(valueToString(o));
                }
                buf.append(", ");
            }
            buf.replace(buf.length() - 2, buf.length(), "");
        }

        buf.append("]'");

        return buf.toString();
    }

    public String mapToString(Map<?, ?> map) {
        final StringBuilder buf = new StringBuilder();
        buf.append("{");

        if (!map.isEmpty()) {
            for (Entry<?, ?> o : map.entrySet()) {
                final Object v = o.getValue();

                if (v != null) {
                    buf.append("'").append(o.getKey()).append("': ");
                    if (ClassUtils.isPrimitiveOrWrapper(v.getClass()) || v instanceof String) {
                        buf.append("'").append(v).append("'");
                    } else {
                        buf.append(valueToString(v));
                    }
                    buf.append(", ");
                }
            }
            buf.replace(buf.length() - 2, buf.length(), "");
        }

        buf.append("}");

        return buf.toString();
    }

}
