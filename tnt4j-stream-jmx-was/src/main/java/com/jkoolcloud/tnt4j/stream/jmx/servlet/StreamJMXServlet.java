/*
 * Copyright 2014-2017 JKOOL, LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jkoolcloud.tnt4j.stream.jmx.servlet;

import static org.apache.commons.lang3.StringEscapeUtils.escapeHtml4;

import java.io.*;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import com.jkoolcloud.tnt4j.config.TrackerConfigStore;
import com.jkoolcloud.tnt4j.stream.jmx.SamplingAgent;
import com.jkoolcloud.tnt4j.stream.jmx.utils.ConsoleOutputCaptor;
import com.jkoolcloud.tnt4j.utils.Utils;

/**
 * Provides Stream-JMX {@link com.jkoolcloud.tnt4j.stream.jmx.SamplingAgent} used configuration viewing/editing and
 * displays console output. After configuration gets changed, current run of sampling gets stopped and started again
 * applying new configuration.
 *
 * @version $Revision: 1 $
 *
 * @see HttpServlet#init(ServletConfig)
 * @see HttpServlet#doGet(HttpServletRequest, HttpServletResponse)
 * @see HttpServlet#doPost(HttpServletRequest, HttpServletResponse)
 * @see HttpServlet#destroy()
 */
public class StreamJMXServlet extends HttpServlet {
	private static final long serialVersionUID = -8291650473147748942L;

	public enum StreamJMXProperties {
		JMX_SAMPLER_FACTORY("com.jkoolcloud.tnt4j.stream.jmx.sampler.factory", "com.jkoolcloud.tnt4j.stream.jmx.impl.WASSamplerFactory", Display.READ_ONLY  , Scope.SYSTEM),
		VALIDATE_TYPES("com.jkoolcloud.tnt4j.stream.jmx.agent.validate.types", "false"                                                 , Display.READ_ONLY  , Scope.SYSTEM),
		TRACE("com.jkoolcloud.tnt4j.stream.jmx.agent.trace"                  , "true"                                                  , Display.READ_ONLY  , Scope.SYSTEM),
		AO("com.jkoolcloud.tnt4j.stream.jmx.agent.options"                   , "*:*!!60000!0"                                          , Display.READ_ONLY  , Scope.LOCAL),
		AO_INCLUDE("com.jkoolcloud.tnt4j.stream.jmx.agent.options.include"   , "*:*"                                                   ,                      Scope.SYNTHETIC, Scope.LOCAL),
		AO_EXCLUDE("com.jkoolcloud.tnt4j.stream.jmx.agent.options.exclude"   , ""                                                      ,                      Scope.SYNTHETIC, Scope.LOCAL),
		AO_PERIOD("com.jkoolcloud.tnt4j.stream.jmx.agent.options.period"     , String.valueOf(TimeUnit.SECONDS.toMillis(60))  ,                      Scope.SYNTHETIC, Scope.LOCAL),
		AO_DELAY("com.jkoolcloud.tnt4j.stream.jmx.agent.options.init.delay"  , "0"                                                     ,                      Scope.SYNTHETIC, Scope.LOCAL),
		VM("com.jkoolcloud.tnt4j.stream.jmx.agent.vm"                        , ""                                                      ,                      Scope.LOCAL),
		TNT4J_CONFIG("tnt4j.config"                                          , "tnt4j.properties"                                      ,                      Scope.SYSTEM, Scope.LOCAL),
		TNT4J_CONFIG_CONT("tnt4j.config.contents"                            , ""                                                      , Display.HIDDEN     , Scope.SYNTHETIC),
		USERNAME("com.jkoolcloud.tnt4j.stream.jmx.agent.user"                , ""                                                      , Display.EDITABLE   , Scope.LOCAL),
		PASSWORD("com.jkoolcloud.tnt4j.stream.jmx.agent.pass"                , ""                                                      , Display.EDITABLE_PW, Scope.LOCAL),
		HOST("com.jkoolcloud.tnt4j.stream.jmx.tnt4j.out.host"                , "localhost"                                             , Display.EDITABLE   , Scope.SYSTEM, Scope.LOCAL),
		PORT("com.jkoolcloud.tnt4j.stream.jmx.tnt4j.out.port"                , "6000"                                                  , Display.EDITABLE   , Scope.SYSTEM, Scope.LOCAL);

		String key;
		String defaultValue;
		Display display;
		Scope[] scope;

		private StreamJMXProperties(String key, String defaultValue, Scope... scopes) {
			this(key, defaultValue, Display.EDITABLE, scopes);
		}

		private StreamJMXProperties(String key, String defaultValue, Display display, Scope... scopes) {
			this.key = key;
			this.defaultValue = defaultValue;
			this.display = display;
			this.scope = scopes;
		}

		static StreamJMXProperties[] values(Display... filters) {
			List<StreamJMXProperties> result = new ArrayList<StreamJMXProperties>(StreamJMXProperties.values().length);
			for (Display filter : filters) {
				for (StreamJMXProperties property : StreamJMXProperties.values()) {
					if (property.display.equals(filter)) {
						result.add(property);
					}
				}
			}
			return result.toArray(new StreamJMXProperties[result.size()]);
		}

		public boolean isInScope(Scope scope) {
			return ArrayUtils.contains(this.scope, scope);
		}

	}

	public enum Scope {
		LOCAL, SYNTHETIC, SYSTEM
	}

	public enum Display {
		EDITABLE, READ_ONLY, HIDDEN, EDITABLE_PW
	}

	private Properties inAppCfgProperties = new Properties();
	private static Thread sampler;

	@Override
	public void init(ServletConfig config) throws ServletException {
		ConsoleOutputCaptor.getInstance().start();

		getPropertiesFromContext(config);
		initStream();
		super.init(config);
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		PrintWriter out = resp.getWriter();
		out.println("<html>");
		out.println("<body>");
		out.println("<h1>TNT4J-Stream-JMX</h1>");

		out.println("<form action=\"" + req.getServletPath() + "\" method=\"post\">");
		out.println("<table>");

		out.println("<tr>");
		out.println("<th>Property</th>");
		out.println("<th>Value</th>");
		out.println("<th>Permission</th>");
		out.println("</tr>");
		for (StreamJMXProperties property : StreamJMXProperties.values(Display.EDITABLE)) {
			printTableLine(out, property, true, false);
		}
		for (StreamJMXProperties property : StreamJMXProperties.values(Display.EDITABLE_PW)) {
			printTableLine(out, property, true, true);
		}
		printTableSplitLine(out);
		for (StreamJMXProperties property : StreamJMXProperties.values(Display.READ_ONLY)) {
			printTableLine(out, property, false, false);
		}

		SecurityManager securityManager = System.getSecurityManager();
		boolean permittedChangeIO;
		try {
			securityManager.checkPermission(new RuntimePermission("setIO"));
			permittedChangeIO = true;
		} catch (SecurityException e) {
			permittedChangeIO = false;
		} catch (NullPointerException e) {
			permittedChangeIO = true;
		}

		out.println("<tr><td>Permission SetIO </td> <td></td>");
		out.println("<td>" + permittedChangeIO + " </td>");
		out.println("</tr>");

		out.println("</table><br>");

		out.println("<br>");
		out.println("<input type=\"submit\" value=\"Submit\"><br>");
		out.println("</form>");
		out.println("<br>");
		out.println("<form action=\"" + req.getServletPath() + "\" method=\"post\">");

		outputTNT4JConfig(out);
		out.println("<br>");
		out.println("<input type=\"submit\" value=\"Submit tnt4j.config\"><br>");
		out.println("<br>");
		outputConsole(out);

		out.println("</form>");

		out.println("</body>");
		out.println("</html>");
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		boolean changed = false;
		for (StreamJMXProperties property : StreamJMXProperties.values(Display.EDITABLE, Display.EDITABLE_PW)) {
			String propertyValue = req.getParameter(property.key);
			if (propertyValue != null) {
				changed |= setProperty(property, propertyValue);
				System.out.println("==> Setting property: name=" + property.key + ", value=" + propertyValue);
			}
		}
		String tnt4jConfigContents = req.getParameter(StreamJMXProperties.TNT4J_CONFIG_CONT.key);
		if (tnt4jConfigContents != null) {
			FileOutputStream tnt4jProperties = null;
			try {
				tnt4jProperties = new FileOutputStream(getClass().getClassLoader()
						.getResource(StreamJMXProperties.TNT4J_CONFIG.defaultValue).getFile());
				tnt4jProperties.write(tnt4jConfigContents.getBytes());
				System.setProperty(StreamJMXProperties.TNT4J_CONFIG.key, StreamJMXProperties.TNT4J_CONFIG.defaultValue);
				changed = true;
			} catch (Exception e) {
				System.out.println("!!!!  Failed writing " + StreamJMXProperties.TNT4J_CONFIG.defaultValue + "  !!!!");
			} finally {
				Utils.close(tnt4jProperties);
			}
		}

		if (changed) {
			samplerDestroy();
			samplerStart();
		}

		resp.sendRedirect(req.getContextPath());
	}

	@Override
	public void destroy() {
		System.out.println("######################     Stopping TNT4J-stream-JMX as Servlet   ######################");
		samplerDestroy();

		super.destroy();

		Exception last = null;
		for (StreamJMXProperties prop : StreamJMXProperties.values()) {
			String propertyValue = getProperty(prop.key, null);

			try {
				System.out.println("==> Saving parameters to servlet context...");
				Method method = getServletConfig().getClass().getMethod("setInitParameter", String.class, String.class);

				method.invoke(getServletConfig(), prop.key, propertyValue);
			} catch (Exception e) {
				System.out.println("!!!!!   Save failed " + e.getClass().getName() + " " + e.getMessage() + "   !!!!!");
				last = e;
			}
		}
		if (last != null) {
			last.printStackTrace();
		}

		ConsoleOutputCaptor.getInstance().stop();
	}

	private void printTableLine(PrintWriter out, StreamJMXProperties property, boolean editable, boolean password) {
		String propertyKey = property.key;
		out.println("<tr>");
		out.println("<td>" + propertyKey + " </td>");

		String editableStr = editable ? "" : "readonly disabled";
		String inputType = password ? "password" : "text";
		String text = escapeHtml4(getProperty(propertyKey, ""));
		if (password) {
			text = text.replaceAll(".", "*");
		}

		out.println("<td><input type=\"" + inputType + "\" name=\"" + propertyKey + "\" value=\"" + text + "\" "
				+ editableStr + " size=\"60\"></td>");
		out.println("<td> ");

		if (property.isInScope(Scope.SYSTEM)) {
			out.print(propertyPermitted(propertyKey));
		} else {
			out.print("&nbsp;");
		}

		out.println("  </td>");
		out.println("</tr>");
	}

	private static void printTableSplitLine(PrintWriter out) {
		out.println("<tr>");
		out.println("<td>&nbsp;</td>");
		out.println("<td>&nbsp;</td>");
		out.println("<td>&nbsp;</td>");
		out.println("</tr>");
	}

	private void outputTNT4JConfig(PrintWriter out) throws IOException {
		out.println("<H3>TNT4J config</H3>");
		out.println("<textarea name=\"" + StreamJMXProperties.TNT4J_CONFIG_CONT.key + "\" cols=\"120\" rows=\"55\">");

		String tnt4JConfig = getProperty(StreamJMXProperties.TNT4J_CONFIG.key, null);

		String tnt4jConfigString;
		if (tnt4JConfig.startsWith(TrackerConfigStore.CFG_LINE_PREFIX)) {
			tnt4jConfigString = tnt4JConfig;
		} else {
			tnt4jConfigString = getString(StreamJMXServlet.class.getClassLoader().getResourceAsStream(tnt4JConfig));
		}
		try {
			out.println(tnt4jConfigString);
		} catch (Exception e) {
			out.println("No tnt4j.properties found: " + tnt4JConfig);
		}
		out.println("</textarea>");
	}

	private static void outputConsole(PrintWriter out) throws IOException {
		out.println("<H3>Console Output</H3>");
		out.println("<textarea name=\"tnt4jConfig\" cols=\"120\" rows=\"55\">");
		try {
			out.println(ConsoleOutputCaptor.getInstance().getCaptured());
		} catch (Exception e) {
			out.println("N/A");
			out.println(e.getMessage());
			e.printStackTrace(out);
		}
		out.println("</textarea>");
	}

	private static String getString(InputStream inputStream) throws IOException {
		try {
			ByteArrayOutputStream result = new ByteArrayOutputStream();
			byte[] buffer = new byte[1024];
			int length;
			while ((length = inputStream.read(buffer)) != -1) {
				result.write(buffer, 0, length);
			}
			return result.toString(Utils.UTF8);
		} catch (Exception e) {
			return "N/A";
		}
	}

	private static boolean propertyPermitted(String property) {
		SecurityManager securityManager = System.getSecurityManager();
		boolean permitted;
		try {
			securityManager.checkPropertyAccess(property);
			permitted = true;
		} catch (SecurityException e) {
			permitted = false;
		} catch (NullPointerException e) {
			permitted = true; // NO security manager
		}
		return permitted;
	}

	private void getPropertiesFromContext(ServletConfig servletConfig) {
		System.out.println(">>>>>> Servlet context initial parameters: start");
		@SuppressWarnings("unchecked")
		Enumeration<String> initParameterNames = (Enumeration<String>) servletConfig.getInitParameterNames();
		while (initParameterNames.hasMoreElements()) {
			String initParam = (String) initParameterNames.nextElement();
			System.out.println("==> Parameter found: " + initParam);
		}
		for (StreamJMXProperties prop : StreamJMXProperties.values()) {
			String initParameter = servletConfig.getInitParameter(prop.key);
			if (initParameter != null) {
				setProperty(prop, initParameter);
				System.out.println("==> Property: " + prop.key + " value changed to: " + initParameter);
			}
		}

		System.out.println("<<<<<< Servlet context initial parameters: end");
	}

	private void initStream() {
		System.out.println("######################     Starting TNT4J-stream-JMX as Servlet   ######################");
		try {
			System.out.println(">>>>>>>>>>>>>>>>>>    TNT4J-stream-JMX environment check start   >>>>>>>>>>>>>>>>>>");
			System.out.println("==> J2EE: " + getClassLocation("javax.management.j2ee.statistics.Statistic"));
			System.out.println("==> IBM.ORB: " + getClassLocation("com.ibm.CORBA.MinorCodes"));
			System.out.println("==> IBM.EJB.THIN.CLIENT: " + getClassLocation("com.ibm.tx.TranConstants"));
			System.out.println("==> IBM.ADMIN.CLIENT: " + getClassLocation("com.ibm.ws.pmi.j2ee.StatisticImpl"));
			System.out.println("<<<<<<<<<<<<<<<<<<<    TNT4J-stream-JMX environment check end   <<<<<<<<<<<<<<<<<<<");

			samplerStart();
		} catch (Exception e) {
			System.out.println("!!!!!!!!!!!!     Failed to start TNT4J-stream-JMX    !!!!!!!!!!!!");
			e.printStackTrace(System.out);
		}
	}

	private static URL getClassLocation(Class<?> clazz) {
		return clazz.getResource('/' + clazz.getName().replace('.', '/') + ".class");
	}

	private static URL getClassLocation(String clazzName) {
		return StreamJMXServlet.class.getResource('/' + clazzName.replace('.', '/') + ".class");
	}

	private String getVM() {
		return inAppCfgProperties.getProperty(StreamJMXProperties.VM.key, StreamJMXProperties.VM.defaultValue);
	}

	private String getAO() {
		return inAppCfgProperties.getProperty(StreamJMXProperties.AO.key, StreamJMXProperties.AO.defaultValue);
	}

	private void samplerStart() {
		configure();
		sampler = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					System.out.println("---------------------     Connecting Sampler Agent     ---------------------");
					String vm = getVM();
					String ao = getAO();
					String user = inAppCfgProperties.getProperty(StreamJMXProperties.USERNAME.key,
							StreamJMXProperties.USERNAME.defaultValue);
					String pass = inAppCfgProperties.getProperty(StreamJMXProperties.PASSWORD.key,
							StreamJMXProperties.PASSWORD.defaultValue);
					if (StringUtils.isEmpty(vm)) {
						System.out.println("==> Sampling from local process runner JVM: options=" + ao);
						SamplingAgent.sampleLocalVM(ao, true);
					} else {
						System.out.println("==> Connecting to remote JVM: vm=" + vm + ", options=" + ao + ", user="
								+ user + ", pass=" + pass.replaceAll(".", "*"));
						SamplingAgent.connect(vm, user, pass, ao);
					}
				} catch (Exception e) {
					System.out.println("!!!!!!!!!!!!     Failed to connect Sampler Agent    !!!!!!!!!!!!");
					e.printStackTrace(System.out);
				}
			}
		}, "Stream-JMX_servlet_sampler_thread");
		sampler.start();
	}

	private void configure() {
		for (StreamJMXProperties property : StreamJMXProperties.values()) {
			if (!property.isInScope(Scope.SYNTHETIC)) {
				setProperty(property, getParam(property));
			}
		}
		expandAO(getParam(StreamJMXProperties.AO));
	}

	private static String compileAO(Properties props) {
		StringBuilder sb = new StringBuilder(64);

		sb.append(props.getProperty(StreamJMXProperties.AO_INCLUDE.key, "*.*"));
		sb.append("!");
		sb.append(props.getProperty(StreamJMXProperties.AO_EXCLUDE.key, ""));
		sb.append("!");
		sb.append(props.getProperty(StreamJMXProperties.AO_PERIOD.key, "60000"));
		sb.append("!");
		sb.append(props.getProperty(StreamJMXProperties.AO_DELAY.key, "0"));
		return sb.toString();
	}

	private void expandAO(String ao) {
		String[] args = ao.split("!");
		if (args.length > 0) {
			setProperty(StreamJMXProperties.AO_INCLUDE, args[0]);
		}
		if (args.length > 1) {
			setProperty(StreamJMXProperties.AO_EXCLUDE, args[1]);
		}
		if (args.length > 2) {
			setProperty(StreamJMXProperties.AO_PERIOD, args[2]);
		}
		if (args.length > 3) {
			setProperty(StreamJMXProperties.AO_DELAY, args[3]);
		}
	}

	private String getProperty(String key, String defValue) {
		for (StreamJMXProperties property : StreamJMXProperties.values()) {
			if (property.key.equals(key)) {
				if (ArrayUtils.contains(property.scope, Scope.SYSTEM)) {
					return System.getProperty(property.key, defValue);
				}
				if (ArrayUtils.contains(property.scope, Scope.LOCAL)) {
					return inAppCfgProperties.getProperty(property.key, defValue);
				}
			}
		}
		return null;
	}

	private String getParam(StreamJMXProperties property) {
		String systemPropertyKey = property.key;

		String pValue = null;
		if (pValue == null) {
			pValue = inAppCfgProperties.getProperty(systemPropertyKey);
		}

		if (pValue == null) {
			try {
				pValue = System.getProperty(systemPropertyKey);
			} catch (SecurityException e) {
				System.out.println(e.getMessage() + "\n " + systemPropertyKey);
			}
		}

		if (pValue == null) {
			pValue = String.valueOf(property.defaultValue);
		}

		if (pValue == null) {
			System.out.println("!!!!!!!!!!!!     Failed to get property " + systemPropertyKey + "    !!!!!!!!!!!!");
		}
		return pValue;
	}

	private void samplerDestroy() {
		System.out.println("------------------------     Destroying Sampler Agent     ------------------------");
		SamplingAgent.destroy();
		try {
			sampler.join(TimeUnit.SECONDS.toMillis(2));
		} catch (InterruptedException exc) {
		}
	}

	private boolean setProperty(StreamJMXProperties property, String value) {
		Object last = null;
		if (property.isInScope(Scope.LOCAL)) {
			last = inAppCfgProperties.setProperty(property.key, value);
		}
		if (property.isInScope(Scope.SYSTEM)) {
			last = System.setProperty(property.key, value);
		}
		if (property.isInScope(Scope.SYNTHETIC)) {
			last = setProperty(StreamJMXProperties.AO, compileAO(inAppCfgProperties));
		}
		if (last != null) {
			return true;
		}
		return false;
	}
}
