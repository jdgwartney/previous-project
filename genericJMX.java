
import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Set;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;


public class genericJMX {

	static int logcount;
	
	public static void main(String[] args) throws Exception {
		
		genericJMX cfg = new genericJMX(); 
		
		String configFile = null;
			
		if (args.length != 0) {
			configFile = args[0];
			configFile = configFile.replace("\\",""); // get rid of escape characters
		}
		else {
			error("No configuration file specified. Filename must be specified as the only parameter.");
			System.exit(1);
		}
	
		log("Looking for configuration file: " + configFile + " - relative to current directory: "
				+   System.getProperty("user.dir"));
		 

		FileReader inputFile = null;	
		try {
			inputFile = new FileReader(configFile);
		}
		catch (FileNotFoundException e) {
			error("Input file not found: " + configFile);
			System.exit(2);
		}
		
	    JSONParser parser = new JSONParser();
		JSONObject configuration = null;
		
		try {
		configuration = (JSONObject) parser.parse(inputFile);
		}
		catch (ParseException e) {
			error("Input file has JSON parse error: " + e.getPosition() + " " + e.toString());
			System.exit(4);
		}

		inputFile.close(); 
		
	    String port = (String) configuration.get("port");
	    if (port == null) {System.out.println("No port sepcified"); System.exit(8);}
	    
	    String host = (String) configuration.get("host");
	    if (host == null) {host = "localhost";}
	    
	    
		// Now we have enough data to attempt the JMX connection
		  
		log("Attempting connection to: " + host + " port: "+ port);
	      
		JMXServiceURL serviceURL = new JMXServiceURL(
			                "service:jmx:rmi:///jndi/rmi://" + host + ":" + port + "/jmxrmi");
		JMXConnector jmxc = JMXConnectorFactory.connect(serviceURL); 

        MBeanServerConnection mbsc = jmxc.getMBeanServerConnection();
        
        // Continue to get the rest of the configuration data
	    
	    String interval = (String) configuration.get("interval");
	    if (interval == null) {interval = "5";}

	    String source = (String) configuration.get("source");
	    if (source == null) {

	   	try
	    	{
	    	    InetAddress addr;
	    	    addr = InetAddress.getLocalHost();
	    	    source = addr.getHostName();
	    	}
	    	catch (UnknownHostException ex)
	    	{
	    	    error("source not specified in configuration file and hostname can not be resolved");
	    	    System.exit(16);
	    	}
	    }  	
	    
	

		ArrayList<Metric> mbeans = new ArrayList<Metric>();
    
		JSONArray metrics = (JSONArray) configuration.get("metrics");
		  for (Object o : metrics)
		  {
		    JSONObject config = (JSONObject) o;
		    
		    String mbean_name = (String) config.get("mbean");
		    String attribute = (String) config.get("attribute");
		    String boundary_metric_name = (String) config.get("boundary_metric_name");
		    String metric_type = (String) config.get("metric_type");  
		    if (metric_type == null) {
		    	metric_type = "standard";
		    }
		    
		    if (mbean_name == null || attribute == null || boundary_metric_name == null)
		    {error("Error in metrics definition"); System.exit(1);}
		    
		    // store the mbeans definitions away and store the MBean server connection with them
		    mbeans.add( cfg.new Metric(mbsc, mbean_name, attribute, boundary_metric_name, metric_type));  
		    
		  }
		  

  
	     while (true) {   // Forever
             long timethen = System.currentTimeMillis();
             for(Object object : mbeans) {
		  		 Metric mbean = (Metric) object;
  		         if (mbean.metric_type.equals("delta")) {
  		        	if (!mbean.setDeltaValue()) {continue;}   // for delta metrics there is no value on the first request
  		         }
  		         else {mbean.setCurrentValue();}
		  		 echo(mbean.boundary_metric_name + " " + mbean.displayValue + " " + source);  // This is for Boundary Meter
    		}
            long timenow = System.currentTimeMillis();	
            long elapsed = timenow - timethen;
            log("Time to get the mbeans was: " + elapsed + " ms");
 	        Thread.sleep(1000 * Integer.valueOf(interval) - elapsed -1);    
	      }

		        
	   }
		    
  private static void error(String msg) throws IOException {
	echo(msg);
	log(msg);
  }
  
  private static void echo(String msg) {
       System.out.println(msg);
  }
  
  private static void log(String line) throws IOException {

	  logcount++;
	  long millis = System.currentTimeMillis() ;
	  
	  BufferedOutputStream bout = null;
	  boolean appendflag = true;
	  if (logcount%1000 == 0) {appendflag = false;}
      bout = new BufferedOutputStream( new FileOutputStream("../genericJMX.log",appendflag) );
	  line = new Date(millis) + " " + millis + " " + logcount + " " 
			  + line + System.getProperty("line.separator");
	  bout.write(line.getBytes());
	 bout.close();
   
  }

	
	
	private class Metric {
		String mbean_name;
		String attribute;
		String boundary_metric_name;
		long currentValue;
		long lastValue;
		String displayValue;
		String metric_type;
		Boolean firstTime;
		MBeanServerConnection mbsc;
		
		Metric(MBeanServerConnection mbsc, String mbean_name, String attribute, 
				String boundary_metric_name, String metric_type) {
			this.mbean_name = mbean_name;
			this.attribute = attribute;
			this.boundary_metric_name = boundary_metric_name;
			this.metric_type = metric_type;
			this.firstTime = true;
			this.mbsc = mbsc;
		}
		
		void storeLastValue() {
			lastValue = currentValue;
		}
		
		Boolean setDeltaValue() throws MalformedObjectNameException, AttributeNotFoundException, InstanceNotFoundException, MBeanException, ReflectionException, IOException {
			this.setCurrentValue();
			if (firstTime) {
				this.storeLastValue();
				this.firstTime = false;
				return false;
			}
			else {
				displayValue = String.valueOf(this.currentValue - this.lastValue);
				this.storeLastValue();
				return true;
			}
			
		}
		void setCurrentValue() throws MalformedObjectNameException, IOException, AttributeNotFoundException, InstanceNotFoundException, MBeanException, ReflectionException {
		   	ObjectName myMbeanName = new ObjectName(mbean_name);
	       	
	       	Set<ObjectInstance> mbeans  = this.mbsc.queryMBeans(myMbeanName,null);    // This should only return 1 instance
	       	if (!mbeans.isEmpty()) {
	  
		   	 for (ObjectInstance name : mbeans) {
		    	currentValue = (long) this.mbsc.getAttribute( name.getObjectName(), attribute);
		    	log("Metric current value for " + mbean_name + " " + attribute + " " + currentValue);
		    	if (this.metric_type.equals("percent")) {currentValue = currentValue * 100;}		    		
		     }
		            	
		  	}
		  	else { error("Unable to locate MBean: "+ mbean_name);}
	       	
	       	displayValue = String.valueOf(currentValue);
		
		}
	
	}

}
