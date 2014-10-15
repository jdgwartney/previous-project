genericJMX
==========

Boundary plugin for generic JMX

uses a configuration file to determine what MBeans to get. 
the JSON configuration file looks like:

{"host":"localhost",
 "port":"7199",
 "metrics":
  [
    {  
        "mbean":"org.apache.cassandra.internal:type=FlushWriter",
        "attribute":"PendingTasks",
        "boundary_metric_name":"CS_FW_PT",
    },
    {  
        "mbean":"org.apache.cassandra.internal:type=FlushWriter",
        "attribute":"CompletedTasks",
        "boundary_metric_name":"CS_FW_CT",
    }
  ]
}


The code tries to read confguration.json in the present working directory
