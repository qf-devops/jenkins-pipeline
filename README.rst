
=================
Jenkins jobs
=================


Defined macros
==============


Defined pipelines
=================


Provision pipelines
-------------------


Provision heat stack with MCP lab
`````````````````````````````````

* script: pipeline/provision/heat_mcp_lab.groovy
* macros: common, python, salt, openstack, http


Provision heat stack with MK lab
````````````````````````````````

* script: pipeline/provision/heat_mk_lab.groovy
* macros: common, python, salt, openstack, http


Utility pipelines
-----------------


Run arbitrary Salt process
``````````````````````````

* script: pipeline/utility/jenkins_job.groovy
* macros: common, salt, http


Update Jenkins master jobs
``````````````````````````

* script: pipeline/utility/salt_process.groovy
* macros: common, salt, http
