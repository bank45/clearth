/*******************************************************************************
 * Copyright 2009-2023 Exactpro Systems Limited
 * https://www.exactpro.com
 * Build Software to Test Software
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
 ******************************************************************************/

package com.exactprosystems.clearth.connectivity.connections2.dummy;

import com.exactprosystems.clearth.connectivity.ClearThClient;
import com.exactprosystems.clearth.connectivity.ConnectionException;
import com.exactprosystems.clearth.connectivity.connections2.BasicClearThMessageConnection;
import com.exactprosystems.clearth.connectivity.connections2.ClearThConnectionSettings;
import com.exactprosystems.clearth.utils.SettingsException;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
@XmlAccessorType(XmlAccessType.NONE)
public class DummyMessageConnection extends BasicClearThMessageConnection
{
	public static final String TYPE = "Dummy";
	@XmlElement
	private boolean autoconnect = true;

	public void setAutoconnect(boolean autoconnect)
	{
		this.autoconnect = autoconnect;
	}

	public Object getLastSentMessage()
	{
		return ((DummyClient) client).getLastSentMessage();
	}
	
	@Override
	public DummyConnectionSettings getSettings()
	{
		return (DummyConnectionSettings) settings;
	}
	
	public Object pollFirstFromReceivedMessages() throws InterruptedException
	{
		return ((DummyClient) client).pollFirstReceivedMessage();
	}
	
	public DummyClient getClient()
	{
		return (DummyClient) client;
	}
	
	@Override
	protected ClearThConnectionSettings createSettings()
	{
		return new DummyConnectionSettings();
	}

	@Override
	protected ClearThClient createClient() throws SettingsException, ConnectionException
	{
		return new DummyClient(this);
	}

	@Override
	public boolean isAutoConnect()
	{
		return autoconnect;
	}
}