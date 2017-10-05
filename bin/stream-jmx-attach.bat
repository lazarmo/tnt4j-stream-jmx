@echo off
setlocal

set RUNDIR=%~dp0
set TOOLS_PATH=%JAVA_HOME%\lib\tools.jar
set LIBPATH=%RUNDIR%..\*;%RUNDIR%..\lib\*;%TOOLS_PATH%
set TNT4JOPTS="-Dorg.slf4j.simpleLogger.defaultLogLevel=debug" "-Dcom.jkoolcloud.tnt4j.stream.jmx.agent.trace=true" "-Dtnt4j.dump.on.vm.shutdown=true" "-Dtnt4j.dump.on.exception=true" "-Dtnt4j.dump.provider.default=true"
set TNT4JOPTS=%TNT4JOPTS% "-Dtnt4j.config=%RUNDIR%..\config\tnt4j.properties"

set AGENT_OPTIONS=%2
if "%AGENT_OPTIONS%"=="" set AGENT_OPTIONS=*:*!!10000

@echo on
java %TNT4JOPTS% -classpath "%LIBPATH%" com.jkoolcloud.tnt4j.stream.jmx.SamplingAgent -attach -vm:%1 -ap:.\..\lib\tnt4j-stream-jmx-0.6.0.jar -ao:%AGENT_OPTIONS%