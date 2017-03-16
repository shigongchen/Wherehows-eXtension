/**
 * Copyright 2015 LinkedIn Corp. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 */
package metadata.etl;

import java.io.FileInputStream;
import org.python.core.PyDictionary;
import org.python.core.PyString;
import org.python.core.PySystemState;
import org.python.util.PythonInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wherehows.common.Constant;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;


/**
 * {@code EtlJob} is the interface of all ETL job.
 * It handle the Jython classpath and all configuration process.
 * Each ETL process that implement this interface will have their own extract, transform, load function.
 * Created by zsun on 7/29/15.
 */
public abstract class EtlJob {

  public PythonInterpreter interpreter;
  public Properties prop;
  public ClassLoader classLoader = getClass().getClassLoader();
  protected final Logger logger = LoggerFactory.getLogger(getClass());

  // default location of local test configuration file
  private final static String DEFAULT_CONFIG_FILE_LOCATION = System.getProperty("user.home") + "/.wherehows/local_test.properties";

  /**
   * Constructor for using config file
   * @param appId
   * @param whExecId generated by backend service
   */
  @Deprecated
  public EtlJob(Integer appId, Integer dbId, long whExecId) {
    this(appId, dbId, whExecId, DEFAULT_CONFIG_FILE_LOCATION);
  }

  /**
   * Private constructor for using config file
   * @param appId
   * @param dbId
   * @param whExecId
   * @param configFile
   */
  @Deprecated
  public EtlJob(Integer appId, Integer dbId, long whExecId, String configFile) {
    PySystemState sys = configFromFile(appId, dbId, whExecId, configFile);
    addJythonToPath(sys);
    interpreter = new PythonInterpreter(null, sys);
  }

  /**
   * Used by backend service
   * @param appId nullable
   * @param dbId nullable
   * @param whExecId
   * @param properties
   */
  public EtlJob(Integer appId, Integer dbId, Long whExecId, Properties properties) {
    PySystemState sys = configFromProperties(appId, dbId, whExecId, properties);
    addJythonToPath(sys);
    interpreter = new PythonInterpreter(null, sys);
  }

  private void addJythonToPath(PySystemState pySystemState) {
    URL url = classLoader.getResource("jython");
    if (url != null) {
      File file = new File(url.getFile());
      String path = file.getPath();
      if (path.startsWith("file:")) {
        path = path.substring(5);
      }
      pySystemState.path.append(new PyString(path.replace("!", "")));
    }
  }

  @Deprecated
  private PySystemState configFromFile(Integer appId, Integer dbId, long whExecId, String configFile) {

    prop = new Properties();
    if (appId != null) {
      prop.setProperty(Constant.APP_ID_KEY, String.valueOf(appId));
    }
    if (dbId != null) {
      prop.setProperty(Constant.DB_ID_KEY, String.valueOf(dbId));
    }
    prop.setProperty(Constant.WH_EXEC_ID_KEY, String.valueOf(whExecId));

    try {
      InputStream propFile = new FileInputStream(configFile);
      prop.load(propFile);
      propFile.close();
    } catch (IOException e) {
      logger.error("property file '{}' not found", configFile);
      e.printStackTrace();
    }

    PyDictionary config = new PyDictionary();

    for (String key : prop.stringPropertyNames()) {
      String value = prop.getProperty(key);
      config.put(new PyString(key), new PyString(value));
    }

    PySystemState sys = new PySystemState();
    sys.argv.append(config);
    return sys;
  }

  /**
   * Copy all properties into jython envirenment
   * @param appId
   * @param whExecId
   * @param properties
   * @return PySystemState A PySystemState that contain all the arguments.
   */
  private PySystemState configFromProperties(Integer appId, Integer dbId, Long whExecId, Properties properties) {
    this.prop = properties;
    if (appId != null)
      prop.setProperty(Constant.APP_ID_KEY, String.valueOf(appId));
    if (dbId != null)
      prop.setProperty(Constant.DB_ID_KEY, String.valueOf(dbId));
    prop.setProperty(Constant.WH_EXEC_ID_KEY, String.valueOf(whExecId));
    PyDictionary config = new PyDictionary();
    for (String key : prop.stringPropertyNames()) {
      String value = prop.getProperty(key);
      config.put(new PyString(key), new PyString(value));
    }
    PySystemState sys = new PySystemState();
    sys.argv.append(config);
    return sys;
  }

  public abstract void extract()
    throws Exception;

  public abstract void transform()
    throws Exception;

  public abstract void load()
    throws Exception;

  public void setup()
    throws Exception {
    // redirect error to out
    System.setErr(System.out);
  }

  public void close()
    throws Exception {
    interpreter.cleanup();
    interpreter.close();
  }

  public void run()
    throws Exception {
    setup();
    logger.info("PySystem path: " + interpreter.getSystemState().path.toString());
    extract();
    transform();
    load();
    close();
  }
}
