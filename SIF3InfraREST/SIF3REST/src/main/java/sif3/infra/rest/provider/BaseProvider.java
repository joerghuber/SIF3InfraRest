/*
 * BaseProvider.java
 * Created: 01/10/2013
 *
 * Copyright 2013 Systemic Pty Ltd
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

package sif3.infra.rest.provider;

import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sif3.common.CommonConstants;
import sif3.common.conversion.ModelObjectInfo;
import sif3.common.exception.DataTooLargeException;
import sif3.common.exception.PersistenceException;
import sif3.common.exception.SIFException;
import sif3.common.exception.UnsupportedQueryException;
import sif3.common.header.HeaderProperties;
import sif3.common.interfaces.EventProvider;
import sif3.common.interfaces.Provider;
import sif3.common.model.PagingInfo;
import sif3.common.model.RequestMetadata;
import sif3.common.model.SIFContext;
import sif3.common.model.SIFZone;
import sif3.infra.common.env.types.EnvironmentInfo.EnvironmentType;

/**
 * This is the main class each specific provider of a given SIF Object type must extends to implement the CRUD operation as defined
 * by the SIF3 specification. It is a base implementation of the request connector. It implements some common functionality each provider
 * may require. It also ensures that all components of a provider service are wired up correctly within the framework. Please refer to 
 * the Developer's Guide for some more details about this class.
 *
 * @author Joerg Huber
 */
public abstract class BaseProvider extends CoreProvider implements Provider, Runnable
{
    protected final Logger logger = LoggerFactory.getLogger(getClass());

	private Timer eventTimer = null;
	private TimerTask eventTimerTask = null;
	
    /**
     */
    public BaseProvider()
    {
	    super();
	    
		//Check a few things to ensure that all core methods are implemented.
		if (getMarshaller() == null)
		{
			logger.error("Provider "+getProviderName()+" has not implemented the getMarshaller() method properly. It returns null which is not valid.");
		}
		if (getUnmarshaller() == null)
		{
			logger.error("Provider "+getProviderName()+" has not implemented the getUnmarshaller() method properly. It returns null which is not valid.");
		}
		if (getSingleObjectClassInfo() == null)
		{
			logger.error("Provider "+getProviderName()+" has not implemented the getSingleObjectClassInfo() method properly. It returns null which is not valid.");
		}
		if (getMultiObjectClassInfo() == null)
		{
			logger.error("Provider "+getProviderName()+" has not implemented the getMultiObjectClassInfo() method properly. It returns null which is not valid.");
		}
    }
     
    public String getServiceName()
    {
    	return getMultiObjectClassInfo().getObjectName();
    }
    
    public ModelObjectInfo getCollectionObjectClassInfo()
    {
        return getMultiObjectClassInfo();
    }

    /**
     * (non-Javadoc)
     * @see sif3.common.interfaces.Provider#finalise()
     */
    public void finalise()
    {
    	// Shut down event timer & task - thread
    	if (eventTimerTask != null)
    	{
    		logger.debug("Shut Down event task for: "+getProviderName());
    		eventTimerTask.cancel();
    		eventTimerTask = null;
    	}
    	if (eventTimer != null)
    	{
    		logger.debug("Shut Down event timer for: "+getProviderName());
    		eventTimer.cancel();
    		eventTimer.purge();
    		eventTimer = null;
    	}
    	
    	// Call finalise on sub-class.
    }
    
    /* (non-Javadoc)
     * @see sif3.common.interfaces.Provider#getServiceInfo(sif3.common.model.SIFZone, sif3.common.model.SIFContext, sif3.common.model.PagingInfo, sif3.common.model.RequestMetadata)
     */
    @Override
    public HeaderProperties getServiceInfo(SIFZone zone, SIFContext context, PagingInfo pagingInfo, RequestMetadata metadata) throws PersistenceException, UnsupportedQueryException, DataTooLargeException, SIFException
    {
        // For the time being we just call the abstract method getCustomServiceInfo().
        return getCustomServiceInfo(zone, context, pagingInfo, metadata);
    }

    /*----------------------------------------*/
    /* Implemented Method for Multi-threading */
    /*----------------------------------------*/

	/**
	 * This method is all that is needed to run the provider in its own thread. The thread is executed at
	 * given intervals driven by a property in the adapter's property file. The interval/frequency
	 * defined in there is used to determine how often this thread is run.
	 */
    @Override
    public final synchronized void run()
    {
    	String providerName = getProviderName();
    	boolean checkEnvType = getServiceProperties().getPropertyAsBool("provider.check.envType", true);
    	
		logger.debug("Start "+providerName+ " thread....");
    	
    	//Only if environment does support events we will start the event manager
    	if (getProviderEnvironment().getEventsSupported())
    	{	
        	// If this is a DIRECT environment then events are not supported, yet.
        	if ((getProviderEnvironment().getEnvironmentType() == EnvironmentType.DIRECT) && checkEnvType)
        	{
            	logger.info("The DIRECT Provider for this framework does NOT support events, yet.");
        	}
        	else
        	{
            	// Check if the provider implements the events interface. Only then events might be required.
    	    	if (EventProvider.class.isAssignableFrom(getClass()))
    	    	{
		        	int frequency = getEventFrequency(providerName);    	
		    		if (frequency != CommonConstants.NO_EVENT)
		    		{
			    		logger.debug("Events supported for this "+providerName+". Start up event thread.");
		    	    	startupEventManager(providerName, frequency);
		    		}
		    		else
		    		{
			    		logger.info("Events supported for  "+providerName+" but currently turned off (frequency=0)");
		    		}
    	    	}
    	    	else
    	    	{
    	        	logger.debug("Events NOT supported for "+providerName+". Provider does not implement EventProvider interface.");
    	    	}
        	}
    	}
    	else
    	{
        	logger.debug("Environment "+getProviderEnvironment().getEnvironmentName()+ " does NOT support events.");    		
    	}
		logger.debug(providerName+" started.");
    }

    /*---------------------*/
    /*-- Private methods --*/
    /*---------------------*/

	/**
	 * This method initialises and schedules the event producer task.
	 */
    //TODO: JH - Consider if I should use Executors.newSingleThreadScheduledExecutor style task/timers here.
	private void startupEventManager(String providerName, int frequencyInSec)
	{
		int period = frequencyInSec * CommonConstants.MILISEC;  // repeat every so often (multiply with milliseconds).
		int delayInSec = getEventDelay(providerName);
		
		logger.info(providerName+".startupEventManager: Event Frequency = " + frequencyInSec + " secs; Event Startup Delay = "+delayInSec+" secs.");
		if (eventTimerTask == null) // not created started
		{
			eventTimerTask = new TimerTask() 
			{
				public void run() 
				{
					logger.debug("Start Event Timer Task for "+getMultiObjectClassInfo().getObjectName()+".");
					((BaseEventProvider<?>)BaseProvider.this).broadcastEvents();
				}
			};
			
			// Now start scheduling events
			logger.debug("Start sending "+getMultiObjectClassInfo().getObjectName()+" events... (Total running threads = "+Thread.activeCount()+")");
			eventTimer = new Timer(true);
			eventTimer.scheduleAtFixedRate(eventTimerTask, delayInSec * CommonConstants.MILISEC, period);
		}
	}
	
	/* 0 = No events */
	private int getEventFrequency(String providerName)
	{
    	return getServiceProperties().getPropertyAsInt(CommonConstants.FREQ_PROPERTY, providerName, CommonConstants.NO_EVENT);    	
	}

	private int getEventDelay(String providerName)
	{
    	int delay = getServiceProperties().getPropertyAsInt(CommonConstants.EVENT_DELAY_PROPERTY, providerName, CommonConstants.DEFAULT_EVENT_DELAY);
    	return (delay < CommonConstants.DEFAULT_EVENT_DELAY) ? CommonConstants.DEFAULT_EVENT_DELAY : delay;
	}
}
