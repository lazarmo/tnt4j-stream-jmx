/*
 * Copyright 2015-2017 JKOOL, LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jkoolcloud.tnt4j.stream.jmx.utils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;

import com.jkoolcloud.tnt4j.core.Property;
import com.jkoolcloud.tnt4j.core.Snapshot;

/**
 * General utility methods used by TNT4J-Stream-JMX.
 *
 * @version $Revision: 1 $
 */
public class Utils extends com.jkoolcloud.tnt4j.utils.Utils {
	public static final String INTERNAl_PROP_PREFIX = "<==";
	public static final String INTERNAl_PROP_SUFFIX = "==>";

	public static final String OBJ_NAME_PROP = makeInternalPropKey("OBJ_NAME_PROP");

	/**
	 * Loads properties from resource with given name.
	 *
	 * @param name
	 *            the resource name
	 * @return properties loaded from resource
	 * @throws java.io.IOException
	 *             if an error occurred when reading properties file
	 *
	 * @see java.lang.ClassLoader#getResourceAsStream(String)
	 * @see java.util.Properties#load(java.io.InputStream)
	 */
	public static Properties loadPropertiesResource(String name) throws IOException {
		Properties rProps = new Properties();

		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		InputStream ins = loader.getResourceAsStream(name);

		try {
			rProps.load(ins);
		} finally {
			close(ins);
		}

		return rProps;
	}

	/**
	 * Loads properties from all resource with given name.
	 *
	 * @param name
	 *            the resource name
	 * @return properties loaded from all found resources
	 * @throws java.io.IOException
	 *             if an error occurred when reading properties file
	 *
	 * @see java.lang.ClassLoader#getResources(String)
	 * @see java.util.Properties#load(java.io.InputStream)
	 */
	public static Properties loadPropertiesResources(String name) throws IOException {
		Properties rProps = new Properties();

		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		Enumeration<URL> rEnum = loader.getResources(name);

		while (rEnum.hasMoreElements()) {
			InputStream ins = rEnum.nextElement().openStream();

			try {
				rProps.load(ins);
			} finally {
				close(ins);
			}
		}

		return rProps;
	}

	/**
	 * Finds snapshot contained property by defined property name ignoring case.
	 * 
	 * @param snapshot
	 *            property snapshot instance
	 * @param propName
	 *            property name
	 * @return snapshot contained property
	 */
	public static Property getSnapPropertyIgnoreCase(Snapshot snapshot, String propName) {
		for (Property prop : snapshot.getSnapshot()) {
			if (prop.getKey().equalsIgnoreCase(propName)) {
				return prop;
			}
		}

		return null;
	}

	/**
	 * Resolves value from {@link System} defined property or {@code dProps} properties. First is checked in
	 * {@link System} properties and if not found, then value is taken from {@code dProps} instance. If none is set in
	 * both properties instances - {@code null} is returned.
	 *
	 * @param dProps
	 *            default properties values
	 * @param propName
	 *            property name
	 * @return system or default properties resolved value
	 *
	 * @see #getConfProperty(Properties, String, String)
	 */
	public static String getConfProperty(Properties dProps, String propName) {
		return getConfProperty(dProps, propName, null);
	}

	/**
	 * Resolves value from {@link System} defined property or {@code dProps} properties. First is checked in
	 * {@link System} properties and if not found, then value is taken from {@code dProps} instance. If none is set in
	 * both properties instances - {@code defValue} is returned.
	 *
	 * @param dProps
	 *            default values properties instance
	 * @param propName
	 *            property name
	 * @param defValue
	 *            default value if none is set in properties
	 * @return system or default properties resolved value
	 *
	 * @see System#getProperty(String)
	 */
	public static String getConfProperty(Properties dProps, String propName, String defValue) {
		String pValue = System.getProperty(propName);

		if (StringUtils.isEmpty(pValue) && dProps != null) {
			pValue = dProps.getProperty(propName, defValue);
		}

		return pValue;
	}

	/**
	 * Makes internally used property key by surrounding provided {@code propName} with {@value #INTERNAl_PROP_PREFIX}
	 * and {@value #INTERNAl_PROP_SUFFIX}.
	 *
	 * @param propName
	 *            property name
	 * @return internally used property key
	 */
	public static String makeInternalPropKey(String propName) {
		return INTERNAl_PROP_PREFIX + propName + INTERNAl_PROP_SUFFIX;
	}

}
