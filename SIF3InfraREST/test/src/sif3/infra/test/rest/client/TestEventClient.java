/*
 * TestEventClient.java
 * Created: 21/03/2014
 *
 * Copyright 2014 Systemic Pty Ltd
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License 
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

package sif3.infra.test.rest.client;

import java.net.URI;

import sif.dd.au30.conversion.DataModelMarshalFactory;
import sif.dd.au30.conversion.DataModelUnmarshalFactory;
import sif.dd.au30.model.StudentCollectionType;
import sif3.common.conversion.MarshalFactory;
import sif3.common.conversion.UnmarshalFactory;
import sif3.common.exception.ServiceInvokationException;
import sif3.common.exception.UnmarshalException;
import sif3.common.header.HeaderValues.EventAction;
import sif3.common.model.SIFEvent;
import sif3.common.model.SIFZone;
import sif3.common.persist.model.SIF3Session;
import sif3.infra.common.env.types.AdapterEnvironmentStore;
import sif3.infra.common.env.types.ConsumerEnvironment.ConnectorName;
import sif3.infra.common.env.types.ProviderEnvironment;
import sif3.infra.rest.client.EventClient;
import au.com.systemic.framework.utils.FileReaderWriter;

/**
 * @author Joerg Huber
 *
 */
public class TestEventClient
{
	private final static String MULTI_STUDENT_FILE_NAME = "C:/Development/GitHubRepositories/SIF3InfraRest/SIF3InfraREST/TestData/xml/input/StudentPersonals5.xml";
	private static final String PROP_FILE_NAME="BrokeredSISProvider";
//	private static final String EVENT_CONNECTOR_URI = "https://systemic.hostedzone.com/svcs/systemicDemo/events";
//	private static final String SESSION_TOKEN = "a4b30e4b-928c-4edb-8b84-148efd634fa5";
//	private static final String PWD="DemoSIS1";
//	private static final String ZONE="demo";
	
	private static final String EVENT_CONNECTOR_URI = "https://australia.hostedzone.com/svcs/loadTest/events";
	private static final String SESSION_TOKEN = "be06a10e-1771-47c3-99bc-52a30b34711b";
	private static final String PWD="ljk32sdfFlks328as";
	private static final String ZONE="loadZone";
	
	private MarshalFactory marshaller = new DataModelMarshalFactory();
	private UnmarshalFactory unmarshaller = new DataModelUnmarshalFactory();
	private AdapterEnvironmentStore store = new AdapterEnvironmentStore(PROP_FILE_NAME);

	
	private StudentCollectionType getStudents()
	{
		String inputEnvXML = FileReaderWriter.getFileContent(MULTI_STUDENT_FILE_NAME);
		//System.out.println("File content:\n" + inputEnvXML);
		try
		{
			return (StudentCollectionType)unmarshaller.unmarshalFromXML(inputEnvXML, StudentCollectionType.class);
		}
		catch (UnmarshalException ex)
		{
			ex.printStackTrace();
			return null;
		}
	}
	
	private SIFEvent<StudentCollectionType> getEvents()
	{
		SIFEvent<StudentCollectionType> events = new SIFEvent<StudentCollectionType>();
		events.setEventAction(EventAction.UPDATE);
		//events.setUpdateType(UpdateType.FULL); // If this is not set then the value from the property file should be used.
		events.setSIFObjectList(getStudents());
		events.setListSize(events.getSIFObjectList().getStudentPersonal().size());
		
		return events;
	}

	private URI getEventConnectorURI()
	{
		try
		{
			return new URI(EVENT_CONNECTOR_URI);
		}
		catch (Exception ex)
		{
			return null;
		}
	}
	
	/*
	 * Just fake a SIF3Session.
	 */
	private SIF3Session getSIF3Session()
	{
		SIF3Session session = new SIF3Session();
		session.setSessionToken(SESSION_TOKEN);
		session.setPassword(PWD);
		session.setDefaultZone(new SIFZone(ZONE, true));
		
		return session;
	}

	private EventClient getEventClient()
	{
		store.getEnvironment().addConnectorBaseURI(ConnectorName.eventsConnector, getEventConnectorURI());
		return new EventClient((ProviderEnvironment)store.getEnvironment(), getSIF3Session(), "StudentPersonals", marshaller);
	}
	
	private void sendEvent(EventClient evtClt) throws ServiceInvokationException
	{
		System.out.println(evtClt.sendEvents(getEvents(), null, null));
	}
	
	public static void main(String[] args)
	{
		try
		{
			TestEventClient tester = new TestEventClient();
		    
		    System.out.println("Start Testing TestEventClient...");
		    
		    EventClient evtClt = tester.getEventClient();
		    for (int i=0; i<5; i++)
		    {	
		    	tester.sendEvent(evtClt);
		    }
		    
		    System.out.println("End Testing TestEventClient.");
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
	}
}