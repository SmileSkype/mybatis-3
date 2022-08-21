/**
 *    Copyright 2009-2019 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.datasource.unpooled;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.apache.ibatis.io.Resources;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 * 实现DataSource接口,非池化的DataSource对象
 *
 * UNPOOLED– 这个数据源的实现只是每次被请求时打开和关闭连接。虽然有点慢，但对于在数据库连接可用性方面没有太高要求的简单应用程序来说，是一个很好的选择。 不同的数据库在性能方面的表现也是不一样的，对于某些数据库来说，使用连接池并不重要，这个配置就很适合这种情形。UNPOOLED 类型的数据源仅仅需要配置以下 5 种属性：
 * driver – 这是 JDBC 驱动的 Java 类的完全限定名（并不是 JDBC 驱动中可能包含的数据源类）。
 * url – 这是数据库的 JDBC URL 地址。
 * username – 登录数据库的用户名。
 * password – 登录数据库的密码。
 * defaultTransactionIsolationLevel – 默认的连接事务隔离级别。
 * 作为可选项，你也可以传递属性给数据库驱动。要这样做，属性的前缀为“driver.”，例如：
 *    driver.encoding=UTF8
 * 这将通过 DriverManager.getConnection(url,driverProperties) 方法传递值为 UTF8 的 encoding 属性给数据库驱动。

 */
public class UnpooledDataSource implements DataSource {

  /**
   * Driver 类加载器
   */
  private ClassLoader driverClassLoader;
  /**
   * Driver 属性
   */
  private Properties driverProperties;
  /**
   * 已注册的Driver映射
   * key: Driver类名
   * value:Driver对象
   */
  private static Map<String, Driver> registeredDrivers = new ConcurrentHashMap<>();
  /**
   * Driver 类名
   */
  private String driver;
  /**
   * 数据库 URL
   */
  private String url;
  /**
   * 数据库用户名
   */
  private String username;
  /**
   * 数据库密码
   */
  private String password;
  /**
   * 是否自动提交事务
   */
  private Boolean autoCommit;
  /**
   * 默认事务隔离级别
   */
  private Integer defaultTransactionIsolationLevel;

  static {
    // 初始化 registeredDrivers
    Enumeration<Driver> drivers = DriverManager.getDrivers();
    while (drivers.hasMoreElements()) {
      Driver driver = drivers.nextElement();
      registeredDrivers.put(driver.getClass().getName(), driver);
    }
  }

  public UnpooledDataSource() {
  }

  public UnpooledDataSource(String driver, String url, String username, String password) {
    this.driver = driver;
    this.url = url;
    this.username = username;
    this.password = password;
  }

  public UnpooledDataSource(String driver, String url, Properties driverProperties) {
    this.driver = driver;
    this.url = url;
    this.driverProperties = driverProperties;
  }

  public UnpooledDataSource(ClassLoader driverClassLoader, String driver, String url, String username, String password) {
    this.driverClassLoader = driverClassLoader;
    this.driver = driver;
    this.url = url;
    this.username = username;
    this.password = password;
  }

  public UnpooledDataSource(ClassLoader driverClassLoader, String driver, String url, Properties driverProperties) {
    this.driverClassLoader = driverClassLoader;
    this.driver = driver;
    this.url = url;
    this.driverProperties = driverProperties;
  }

  /**
   * 获得Connection连接
   */
  @Override
  public Connection getConnection() throws SQLException {
    return doGetConnection(username, password);
  }

  @Override
  public Connection getConnection(String username, String password) throws SQLException {
    return doGetConnection(username, password);
  }

  @Override
  public void setLoginTimeout(int loginTimeout) {
    DriverManager.setLoginTimeout(loginTimeout);
  }

  @Override
  public int getLoginTimeout() {
    return DriverManager.getLoginTimeout();
  }

  @Override
  public void setLogWriter(PrintWriter logWriter) {
    DriverManager.setLogWriter(logWriter);
  }

  @Override
  public PrintWriter getLogWriter() {
    return DriverManager.getLogWriter();
  }

  public ClassLoader getDriverClassLoader() {
    return driverClassLoader;
  }

  public void setDriverClassLoader(ClassLoader driverClassLoader) {
    this.driverClassLoader = driverClassLoader;
  }

  public Properties getDriverProperties() {
    return driverProperties;
  }

  public void setDriverProperties(Properties driverProperties) {
    this.driverProperties = driverProperties;
  }

  public String getDriver() {
    return driver;
  }

  public synchronized void setDriver(String driver) {
    this.driver = driver;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public Boolean isAutoCommit() {
    return autoCommit;
  }

  public void setAutoCommit(Boolean autoCommit) {
    this.autoCommit = autoCommit;
  }

  public Integer getDefaultTransactionIsolationLevel() {
    return defaultTransactionIsolationLevel;
  }

  public void setDefaultTransactionIsolationLevel(Integer defaultTransactionIsolationLevel) {
    this.defaultTransactionIsolationLevel = defaultTransactionIsolationLevel;
  }

  private Connection doGetConnection(String username, String password) throws SQLException {
    // 创建 Properties 对象
    Properties props = new Properties();
    // 设置 driverProperties 到 props 中
    if (driverProperties != null) {
      props.putAll(driverProperties);
    }
    // 设置 user 和 password 到 props 中
    if (username != null) {
      props.setProperty("user", username);
    }
    if (password != null) {
      props.setProperty("password", password);
    }
    // 执行获得 Connection 连接
    return doGetConnection(props);
  }

  private Connection doGetConnection(Properties properties) throws SQLException {
    // <1> 初始化 Driver
    initializeDriver();
    // <2> 获得 Connection 对象
    Connection connection = DriverManager.getConnection(url, properties);
    // <3> 配置 Connection 对象
    configureConnection(connection);
    return connection;
  }

  /**
   *总体逻辑比较简单，判断 registeredDrivers 是否已经存在该 driver ？若不存在，进行初始化。
   * <1> 处，synchronized 锁的粒度太大，可以减小到基于 registeredDrivers 来同步，并且很多时候，不需要加锁。
   * <2> 处，获得 driver 类，实际上，就是我们常见的 "Class.forName("com.mysql.jdbc.Driver")" 。
   * <3> 处，创建 Driver 对象，并注册到 DriverManager 中，以及添加到 registeredDrivers 中。为什么此处会有使用 DriverProxy 呢？DriverProxy 的代码如下：
   */
  private synchronized void initializeDriver() throws SQLException {
    // 判断 registeredDrivers 是否已经存在该 driver ，若不存在，进行初始化
    if (!registeredDrivers.containsKey(driver)) {
      Class<?> driverType;
      try {
        // <2> 获得 driver 类
        if (driverClassLoader != null) {
          driverType = Class.forName(driver, true, driverClassLoader);
        } else {
          driverType = Resources.classForName(driver);
        }
        // DriverManager requires the driver to be loaded via the system ClassLoader.
        // http://www.kfu.com/~nsayer/Java/dyn-jdbc.html
        // <3> 创建 Driver 对象
        Driver driverInstance = (Driver)driverType.newInstance();
        // 创建 DriverProxy 对象，并注册到 DriverManager 中
        DriverManager.registerDriver(new DriverProxy(driverInstance));
        // 添加到 registeredDrivers 中
        registeredDrivers.put(driver, driverInstance);
      } catch (Exception e) {
        throw new SQLException("Error setting driver on UnpooledDataSource. Cause: " + e);
      }
    }
  }

  private void configureConnection(Connection conn) throws SQLException {
    // 设置自动提交
    if (autoCommit != null && autoCommit != conn.getAutoCommit()) {
      conn.setAutoCommit(autoCommit);
    }
    // 设置事务隔离级别
    if (defaultTransactionIsolationLevel != null) {
      conn.setTransactionIsolation(defaultTransactionIsolationLevel);
    }
  }

  private static class DriverProxy implements Driver {
    private Driver driver;

    DriverProxy(Driver d) {
      this.driver = d;
    }

    @Override
    public boolean acceptsURL(String u) throws SQLException {
      return this.driver.acceptsURL(u);
    }

    @Override
    public Connection connect(String u, Properties p) throws SQLException {
      return this.driver.connect(u, p);
    }

    @Override
    public int getMajorVersion() {
      return this.driver.getMajorVersion();
    }

    @Override
    public int getMinorVersion() {
      return this.driver.getMinorVersion();
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String u, Properties p) throws SQLException {
      return this.driver.getPropertyInfo(u, p);
    }

    @Override
    public boolean jdbcCompliant() {
      return this.driver.jdbcCompliant();
    }

    // @Override only valid jdk7+
    // <4> 使用 MyBatis 自定义的 Logger 对象。
    //其他方法，实际就是直接调用 driver 对应的方法
    public Logger getParentLogger() {
      return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    }
  }

  @Override
  public <T> T unwrap(Class<T> iface) throws SQLException {
    throw new SQLException(getClass().getName() + " is not a wrapper.");
  }

  @Override
  public boolean isWrapperFor(Class<?> iface) throws SQLException {
    return false;
  }

  // @Override only valid jdk7+
  public Logger getParentLogger() {
    // requires JDK version 1.6
    return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
  }

}
