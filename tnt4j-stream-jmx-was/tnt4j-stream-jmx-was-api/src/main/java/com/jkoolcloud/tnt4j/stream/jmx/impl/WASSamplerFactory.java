/*
 * Copyright 2015-2018 JKOOL, LLC.
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
package com.jkoolcloud.tnt4j.stream.jmx.impl;

import java.util.Map;

import javax.management.MBeanServerConnection;

import org.slf4j.bridge.SLF4JBridgeHandler;

import com.jkoolcloud.tnt4j.stream.jmx.conditions.SampleHandler;
import com.jkoolcloud.tnt4j.stream.jmx.core.SampleListener;
import com.jkoolcloud.tnt4j.stream.jmx.core.Sampler;
import com.jkoolcloud.tnt4j.stream.jmx.scheduler.WASSampleHandlerImpl;
import com.jkoolcloud.tnt4j.stream.jmx.utils.WASSecurityHelper;
import com.jkoolcloud.tnt4j.utils.Utils;

/**
 * <p>
 * This class provides a {@link com.jkoolcloud.tnt4j.stream.jmx.factory.SamplerFactory} implementation with
 * {@link WASJmxSampler} as underlying sampler implementation and
 * {@code com.jkoolcloud.tnt4j.stream.jmx.format.SLIFactPathValueFormatter} as samples formatter.
 * </p>
 *
 * @version $Revision: 1 $
 * 
 * @see Sampler
 * @see WASJmxSampler
 * @see WASSampleListener
 */
public class WASSamplerFactory extends J2EESamplerFactory {

	private boolean local = false;

	@Override
	public Sampler newInstance() {
		local = true;
		return new WASJmxSampler(this);
	}

	@Override
	public void initialize() {
		boolean redirectJULToStreamLog = Utils.getBoolean(
				"com.jkoolcloud.tnt4j.stream.jmx.sampler.redirectJULToStreamLog", System.getProperties(), false);
		if (redirectJULToStreamLog) {
			SLF4JBridgeHandler.removeHandlersForRootLogger();
			SLF4JBridgeHandler.install();
		}
	}

	@Override
	public Sampler newInstance(MBeanServerConnection mServerConn) {
		return mServerConn == null ? newInstance() : new WASJmxSampler(mServerConn, this);
	}

	@Override
	public String defaultEventFormatterClassName() {
		return "com.jkoolcloud.tnt4j.stream.jmx.format.SLIFactPathValueFormatter";
	}

	@Override
	public SampleListener newListener(Map<String, ?> properties) {
		return new WASSampleListener(properties);
	}

	@Override
	public SampleHandler newSampleHandler(MBeanServerConnection mServerConn, String incFilterList,
			String excFilterList) {
		if (local && WASSecurityHelper.isServerSecurityEnabled()) {
			return new WASSampleHandlerImpl(mServerConn, incFilterList, excFilterList);
		}

		return super.newSampleHandler(mServerConn, incFilterList, excFilterList);
	}
}
