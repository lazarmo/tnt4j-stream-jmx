/*
 * Copyright 2014 Nastel Technologies, Inc.
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
package org.tnt4j.pingjmx;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Vector;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;

import com.nastel.jkool.tnt4j.core.Activity;
import com.nastel.jkool.tnt4j.core.ActivityListener;
import com.nastel.jkool.tnt4j.core.PropertySnapshot;

public class PingJmxListener implements ActivityListener {

	String mbeanFilter;
	long sampleCount = 0;
	MBeanServer mbeanServer;
	HashMap<ObjectName, MBeanInfo> mbeans = new HashMap<ObjectName, MBeanInfo>(89);
	HashMap<MBeanAttributeInfo, MBeanAttributeInfo> excAttrs = new HashMap<MBeanAttributeInfo, MBeanAttributeInfo>(89);

	public PingJmxListener(MBeanServer mserver, String filter) {
		mbeanServer = mserver;
		mbeanFilter = filter;
	}

	public MBeanServer getMBeanServer() {
		return mbeanServer;
	}

	private void loadJmxBeans() {
		try {
			StringTokenizer tk = new StringTokenizer(mbeanFilter, ";");
			Vector<ObjectName> nFilters = new Vector<ObjectName>(5);
			while (tk.hasMoreTokens()) {
				nFilters.add(new ObjectName(tk.nextToken()));
			}
			for (ObjectName nameFilter : nFilters) {
				Set<?> set = mbeanServer.queryNames(nameFilter, nameFilter);
				for (Iterator<?> it = set.iterator(); it.hasNext();) {
					ObjectName oname = (ObjectName) it.next();
					mbeans.put(oname, mbeanServer.getMBeanInfo(oname));
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private void sampleMbeans(Activity activity) {
		for (Entry<ObjectName, MBeanInfo> entry: mbeans.entrySet()) {
			ObjectName name = entry.getKey();
			MBeanInfo info = entry.getValue();
			MBeanAttributeInfo[] attr = info.getAttributes();
			
			PropertySnapshot snapshot = new PropertySnapshot(name.getDomain(), name.getCanonicalName());
			for (int i = 0; i < attr.length; i++) {
				MBeanAttributeInfo jinfo = attr[i];
				if (jinfo.isReadable() && !attrExcluded(jinfo)) {
					try {
						Object value = mbeanServer.getAttribute(name, jinfo.getName());
						processJmxValue(snapshot, jinfo, jinfo.getName(), value);
					} catch (Throwable ex) {
						exclude(jinfo);
						System.err.println("Skipping attribute=" + jinfo + ", reason=" + ex);
					}
				}
			}
			if (snapshot.size() > 0) {
				activity.addSnapshot(snapshot);
			}
		}
	}

	private boolean attrExcluded(MBeanAttributeInfo jinfo) {
	    return excAttrs.get(jinfo) != null;
    }

	private void exclude(MBeanAttributeInfo jinfo) {
	    excAttrs.put(jinfo, jinfo);
    }

	private void processJmxValue(PropertySnapshot snapshot, MBeanAttributeInfo jinfo, String propName, Object value) {
		if (value != null && !value.getClass().isArray()) {
			if (value instanceof CompositeData) {
				CompositeData cdata = (CompositeData) value;
				Set<String> keys = cdata.getCompositeType().keySet();
				for (String key: keys) {
					Object cval = cdata.get(key);
					processJmxValue(snapshot, jinfo, propName + "\\" + key, cval);
				}
			} else {
				snapshot.add(propName, value);
			}
		}
	}
	
	private void finish(Activity activity) {
		PropertySnapshot snapshot = new PropertySnapshot(activity.getName(), "SampleStats");
		snapshot.add("sample.count", sampleCount);
		activity.addSnapshot(snapshot);		
	}
	
	@Override
	public void started(Activity activity) {
		if (mbeans.size() == 0) {
			loadJmxBeans();
		}
	}

	@Override
	public void stopped(Activity activity) {
		sampleCount++;
		sampleMbeans(activity);		
		finish(activity);		
		
		System.out.println(activity.getName()
				+ ": activity.id=" + activity.getTrackingId() 
				+ ", elasped.usec=" + activity.getElapsedTime() 
				+ ", snap.count=" + activity.getSnapshotCount() 
				+ ", id.count=" + activity.getIdCount()
				+ ", mbeans.count=" + mbeans.size()
				+ ", exclude.attrs=" + excAttrs.size()
				);
	}
}
