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

import java.io.PrintStream;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.apache.commons.lang3.StringUtils;

import com.ibm.websphere.security.WSSecurityException;
import com.ibm.websphere.security.WSSecurityHelper;
import com.ibm.websphere.security.auth.WSSubject;
import com.ibm.websphere.security.auth.callback.WSCallbackHandlerImpl;
import com.ibm.websphere.security.cred.WSCredential;

/**
 * Helper class used to simplify use of WAS security entities.
 * <p>
 * It allows to use RunAs subject to perform {@link PrivilegedAction}s. Also it provides {@link #login(String, String)}
 * method to use particular user subject when performing {@link PrivilegedAction}s.
 *
 * @version $Revision: 1 $
 */
public class WASSecurityHelper {
	private static final String REALM_NAME = "default";

	private static LoginContext loginContext;
	private static Subject subject = null;

	/**
	 * Logins WAS user by provided username/password.
	 *
	 * @param userName
	 *            user name
	 * @param password
	 *            user password
	 * @throws LoginException
	 *             if the authentication fails
	 *
	 * @see #logout()
	 */
	public static synchronized void login(String userName, String password) throws LoginException {
		if (loginContext == null) {
			loginContext = new LoginContext("Stream-JMX_WSLoginContext",
					new WSCallbackHandlerImpl(userName, REALM_NAME, password));
			loginContext.login();
		}

		subject = loginContext.getSubject();
	}

	/**
	 * Logout user.
	 *
	 * @throws LoginException
	 *             if logout fails
	 *
	 * @see #login(String, String)
	 */
	public static synchronized void logout() throws LoginException {
		if (loginContext != null) {
			loginContext.logout();
			loginContext = null;
		}
	}

	/**
	 * Run some privileged code with logged in user subject.
	 *
	 * @param action
	 *            privileged action to perform
	 * @return result the value returned by the {@link PrivilegedAction#run()} method
	 * @throws WSSecurityException
	 *             if WAS security is enabled and there is no subject provided
	 *
	 * @see WSSubject#doAs(Subject, PrivilegedAction)
	 * @see #doPrivilegedAction(PrivilegedExceptionAction)
	 */
	@SuppressWarnings("unchecked")
	public static <T> T doPrivilegedAction(PrivilegedAction<T> action) throws WSSecurityException {
		checkSubject();

		return (T) WSSubject.doAs(subject, action);
	}

	/**
	 * Run some privileged code with logged in user subject.
	 *
	 * @param action
	 *            privileged exception action to perform
	 * @return result the value returned by the {@link PrivilegedExceptionAction#run()} method
	 * @throws WSSecurityException
	 *             if WAS security is enabled and there is no subject provided
	 * @throws PrivilegedActionException
	 *             if the specified action's {@link PrivilegedExceptionAction#run()} method threw a <i>checked</i>
	 *             exception
	 *
	 * @see WSSubject#doAs(Subject, PrivilegedExceptionAction)
	 * @see #doPrivilegedAction(PrivilegedAction)
	 */
	@SuppressWarnings("unchecked")
	public static <T> T doPrivilegedAction(PrivilegedExceptionAction<T> action)
			throws WSSecurityException, PrivilegedActionException {
		checkSubject();

		return (T) WSSubject.doAs(subject, action);
	}

	private static void checkSubject() throws WSSecurityException {
		if (isServerSecurityEnabled() && subject == null) {
			throw new WSSecurityException("No subject is provided!");
		}
	}

	/**
	 * Prints RunAs subject credentials to provided print stream.
	 *
	 * @param out
	 *            print stream to use for output
	 */
	public static void printCurrentCredentials(PrintStream out) {
		try {
			Subject runAsSubject = WSSubject.getRunAsSubject();
			if (runAsSubject != null) {
				final Iterator<Principal> iterator = runAsSubject.getPrincipals().iterator();
				out.print("Running as: ");
				while (iterator.hasNext()) {
					out.print(iterator.next().getName() + ", ");
				}
			} else {
				out.println("!!!!   Security failure - no subject!..   !!!!");
				return;
			}

			final Set<WSCredential> credentials = runAsSubject.getPublicCredentials(WSCredential.class);

			for (WSCredential cred : credentials) {
				out.println("getSecurityName: " + cred.getSecurityName());
				out.println("getUniqueSecurityName: " + cred.getUniqueSecurityName());
				out.println("getRealmName: " + cred.getRealmName());
				out.println("getRealmSecurityName: " + cred.getRealmSecurityName());
				out.println("getRealmUniqueSecurityName: " + cred.getRealmUniqueSecurityName());
				// always return null
				out.println("getRoles: " + cred.getRoles());
				ArrayList<?> groupIds = cred.getGroupIds();
				out.println("getGroupIds: " + groupIds);
				out.println("=-=-=-=-=-=-=-=-=-=-=-=-=-=");
			}
		} catch (Exception e) {
			out.println("!!!!   Security access failure!..   !!!!");
			e.printStackTrace(out);
		}
	}

	/**
	 * Login current RunAs user, and keep it.
	 *
	 * @throws WSSecurityException
	 *             if WS security occurs while getting RunAs subject, or got {@code null} subject
	 */
	public static void loginCurrent() throws WSSecurityException {
		subject = WSSubject.getRunAsSubject();

		checkSubject();
	}

	/**
	 * Acquires {@link Subject} to be used for a JMX sampling operations.
	 * <p>
	 * If {@code user} and {@code pass} are provided - tries to login using those credentials and get subject. Otherwise
	 * uses RunAs user subject.
	 *
	 * @param user
	 *            user name
	 * @param pass
	 *            user password
	 *
	 * @see #login(String, String)
	 * @see #loginCurrent()
	 */
	public static void acquireSubject(String user, String pass) {
		if (isServerSecurityEnabled()) {
			if (StringUtils.isNotEmpty(user) && StringUtils.isNotEmpty(pass)) {
				System.out.println("==> trying to login manually as: " + user);
				try {
					login(user, pass);
				} catch (LoginException e) {
					System.err.println("!!!!   Failed to login user " + user + " and acquire subject!..   !!!!");
					e.printStackTrace();
				}
			} else {
				try {
					loginCurrent();
				} catch (WSSecurityException e) {
					System.err.println("!!!!   Failed to acquire RunAs subject!..   !!!!");
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * Checks whether WAS global or server security is enabled.
	 * 
	 * @return {@code true} if global or server security is turned on, {@code false} - otherwise
	 */
	public static boolean isServerSecurityEnabled() {
		return WSSecurityHelper.isGlobalSecurityEnabled() || WSSecurityHelper.isServerSecurityEnabled();
	}

	/**
	 * Checks whether Java2 security is enabled.
	 *
	 * @return {@code true} if Java2 security is turned on, {@code false} - otherwise
	 */
	public static boolean isJavaSecurityEnabled() {
		return System.getSecurityManager() != null;
	}
}