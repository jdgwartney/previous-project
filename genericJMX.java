
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

import javax.management.MBeanServerConnection;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;


public class genericJMX {

	static int logcount;
	
	public static void main(String[] args) throws Exception {
		
		String configFile = null;
			
		if (args.length != 0) {
			configFile = args[0];
			configFile = configFile.replace("\\",""); // get rid of escape characters
		}
		else {
			error("No configuration file specified. Filename must be specified as the only parameter.");
			System.exit(1);
		}
		
		genericJMX cfg = new genericJMX();
		ArrayList<Metric> mbeans = new ArrayList<Metric>();
		
	
		//log("Looking for configuration file: " + configFile + " - relative to current directory: "
		//		+   System.getProperty("user.dir"));
		 
	    JSONParser parser = new JSONParser();
	      
		JSONObject configuration = null;
		FileReader inputFile = null;	
		try {
			inputFile = new FileReader(configFile);
		}
		catch (FileNotFoundException e) {
			error("Input file not found: " + configFile);
			System.exit(2);
		}
		
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

	    
		JSONArray metrics = (JSONArray) configuration.get("metrics");
		  for (Object o : metrics)
		  {
		    JSONObject config = (JSONObject) o;
		    
		    String mbean_name = (String) config.get("mbean");
		    String attribute = (String) config.get("attribute");
		    String boundary_metric_name = (String) config.get("boundary_metric_name");
		    
		    if (mbean_name == null || attribute == null || boundary_metric_name == null)
		    {echo("Error in metrics definition"); System.exit(1);}
		    
		    mbeans.add( cfg.new Metric(mbean_name, attribute, boundary_metric_name));  // store my mbeans away
		    
		  }
		  
		  // Now we have the configuration data, let's start using it to get the MBeans
		  
        
	     JMXServiceURL serviceURL = new JMXServiceURL(
		                "service:jmx:rmi:///jndi/rmi://" + host + ":" + port + "/jmxrmi");
	     JMXConnector jmxc = JMXConnectorFactory.connect(serviceURL); 


	     MBeanServerConnection mbsc = jmxc.getMBeanServerConnection();
  
        
	     while (true) {   // Forever
   	 		  for(Object object : mbeans) {
		  		      Metric mbean = (Metric) object;
		  		      getMbean(mbsc, mbean.mbean_name, mbean.attribute, mbean.boundary_metric_name, source);
	  		  }
		  		  
	        	Thread.sleep(1000 * Integer.valueOf(interval));
	      }

		        
	   }
		    
private static void getMbean(MBeanServerConnection mbsc, String searchName, String attributeName, 
   		String displayName, String source) throws Exception
    {
    	ObjectName myMbeanName = new ObjectName(searchName);
       	
       	Set<ObjectInstance> mbeans  = mbsc.queryMBeans(myMbeanName,null);    // This should only return 1 instance
       	if (!mbeans.isEmpty()) {
  
	   	 for (ObjectInstance name: mbeans) {
	    	echo(displayName + " " + mbsc.getAttribute( name.getObjectName(), attributeName).toString() + " " + source);
	     }
	            	
	  	}
	  	else { error("Unable to locate MBean: "+ searchName);}
		    	
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
      bout = new BufferedOutputStream( new FileOutputStream("genericJMX.log",appendflag) );
	  line = new Date(millis) + " " + logcount + " " + line + System.getProperty("line.separator");
	  bout.write(line.getBytes());
	 bout.close();
   
  }

	
	
	private class Metric {
		String mbean_name;
		String attribute;
		String boundary_metric_name;
		
		Metric(String mbean_name, String attribute, String boundary_metric_name) {
			this.mbean_name = mbean_name;
			this.attribute = attribute;
			this.boundary_metric_name = boundary_metric_name;
		}
	
	}

}
