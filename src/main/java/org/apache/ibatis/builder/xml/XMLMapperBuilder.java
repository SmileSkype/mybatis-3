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
package org.apache.ibatis.builder.xml;

import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.builder.CacheRefResolver;
import org.apache.ibatis.builder.IncompleteElementException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.builder.ResultMapResolver;
import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Discriminator;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.ResultFlag;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 * 继承BaseBuilder抽象类,Mapper XML配置构建器,主要负责解析Mapper映射配置文件
 */
public class XMLMapperBuilder extends BaseBuilder {

  /**
   * 基于Java XPath 解析器
   */
  private final XPathParser parser;
  /**
   * Mapper构造器助手  MapperBuilderAssistant 对象，是 XMLMapperBuilder 和 MapperAnnotationBuilder 的小助手，
   * 提供了一些公用的方法，例如创建 ParameterMap、MappedStatement 对象等等
   */
  private final MapperBuilderAssistant builderAssistant;
  /**
   * 可被其他语句引用的可重用语句块的集合
   * 例如：<sql id="userColumns"> ${alias}.id,${alias}.username,${alias}.password </sql>
   */
  private final Map<String, XNode> sqlFragments;
  /**
   * 资源引用的地址
   */
  private final String resource;

  @Deprecated
  public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
    this(reader, configuration, resource, sqlFragments);
    this.builderAssistant.setCurrentNamespace(namespace);
  }

  @Deprecated
  public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    this(new XPathParser(reader, true, configuration.getVariables(), new XMLMapperEntityResolver()),
        configuration, resource, sqlFragments);
  }

  public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
    this(inputStream, configuration, resource, sqlFragments);
    this.builderAssistant.setCurrentNamespace(namespace);
  }

  public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    this(new XPathParser(inputStream, true, configuration.getVariables(), new XMLMapperEntityResolver()),
        configuration, resource, sqlFragments);
  }

  /**
   * 最终会被调用到的构造函数
   */
  private XMLMapperBuilder(XPathParser parser, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    super(configuration);
    // 创建 MapperBuilderAssistant 对象 MapperBuilderAssistant 对象，是 XMLMapperBuilder 和 MapperAnnotationBuilder 的小助手，提供了一些公用的方法，例如创建 ParameterMap、MappedStatement 对象等等
    this.builderAssistant = new MapperBuilderAssistant(configuration, resource);
    this.parser = parser;
    this.sqlFragments = sqlFragments;
    this.resource = resource;
  }

  /**
   * 解析流程
   */
  public void parse() {
    // <1> 判断当前 Mapper 是否已经加载过  这里的resource路径是怎么样的 TODO
    if (!configuration.isResourceLoaded(resource)) {
      // <2> 解析 <mapper> 节点
      configurationElement(parser.evalNode("/mapper"));
      // <3> 标记该Mapper已经加载过
      configuration.addLoadedResource(resource);
      // <4> 绑定Mapper
      bindMapperForNamespace();
    }
    /**
     * <5,6,7> 实际上可以理解为第一次加载时,资源不存在,等加载完资源之后,在重新初始化一遍刚才失败的内容
     * 三个方法的逻辑思路基本一致：1）获得对应的集合；2）遍历集合，执行解析；3）执行成功，则移除出集合；4）执行失败，忽略异常。
     * 当然，实际上，此处还是可能有执行解析失败的情况，但是随着每一个 Mapper 配置文件对应的 XMLMapperBuilder 执行一次这些方法，逐步逐步就会被全部解析完
      */
    // <5> 解析待定的 <resultMap /> 节点
    parsePendingResultMaps();
    // <6> 解析待定的 <cache-ref /> 节点
    parsePendingCacheRefs();
    // <7> 解析待定的 SQL 语句的节点
    parsePendingStatements();
  }

  public XNode getSqlFragment(String refid) {
    return sqlFragments.get(refid);
  }

  /**
   * 解析 <mapper /> 节点
   */
  private void configurationElement(XNode context) {
    try {
      // <1> 获得 namespace 属性
      String namespace = context.getStringAttribute("namespace");
      if (namespace == null || namespace.equals("")) {
        throw new BuilderException("Mapper's namespace cannot be empty");
      }
      // <1> 设置 namespace 属性 到MapperBuilderAssistant中
      builderAssistant.setCurrentNamespace(namespace);
      // <2> 解析 <cache-ref /> 节点
      cacheRefElement(context.evalNode("cache-ref"));
      // <3> 解析 <cache /> 节点
      cacheElement(context.evalNode("cache"));
      // 已废弃！老式风格的参数映射。内联参数是首选,这个元素可能在将来被移除，这里不会记录。
      parameterMapElement(context.evalNodes("/mapper/parameterMap"));
      // <4> 解析 <resultMap /> 节点们
      resultMapElements(context.evalNodes("/mapper/resultMap"));
      // <5> 解析 <sql /> 节点们
      sqlElement(context.evalNodes("/mapper/sql"));
      // <6> 解析 <select /> <insert /> <update /> <delete /> 节点们
      buildStatementFromContext(context.evalNodes("select|insert|update|delete"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing Mapper XML. The XML location is '" + resource + "'. Cause: " + e, e);
    }
  }

  /**
   * 解析  <select|insert|update|delete>
   * @param list
   */
  private void buildStatementFromContext(List<XNode> list) {
    if (configuration.getDatabaseId() != null) {
      buildStatementFromContext(list, configuration.getDatabaseId());
    }
    buildStatementFromContext(list, null);
  }

  /**
   * 解析  <select|insert|update|delete>
   *     加载XMLStatementBuilder
   * @param list
   */
  private void buildStatementFromContext(List<XNode> list, String requiredDatabaseId) {
    // <1> 遍历 <select /> <insert /> <update /> <delete /> 节点们
    for (XNode context : list) {
      // <1> 创建 XMLStatementBuilder 对象，执行解析
      final XMLStatementBuilder statementParser = new XMLStatementBuilder(configuration, builderAssistant, context, requiredDatabaseId);
      try {
        // 执行Statement解析
        statementParser.parseStatementNode();
      } catch (IncompleteElementException e) {
        // <2> 解析失败，添加到 configuration 中
        configuration.addIncompleteStatement(statementParser);
      }
    }
  }

  private void parsePendingResultMaps() {
    // 获得 ResultMapResolver 集合，并遍历进行处理
    Collection<ResultMapResolver> incompleteResultMaps = configuration.getIncompleteResultMaps();
    synchronized (incompleteResultMaps) {
      Iterator<ResultMapResolver> iter = incompleteResultMaps.iterator();
      while (iter.hasNext()) {
        try {
          // 执行解析
          iter.next().resolve();
          // 移除
          iter.remove();
        } catch (IncompleteElementException e) {
          // ResultMap is still missing a resource...
          // 解析失败，不抛出异常
        }
      }
    }
  }

  private void parsePendingCacheRefs() {
    // 获得 CacheRefResolver 集合，并遍历进行处理
    Collection<CacheRefResolver> incompleteCacheRefs = configuration.getIncompleteCacheRefs();
    synchronized (incompleteCacheRefs) {
      Iterator<CacheRefResolver> iter = incompleteCacheRefs.iterator();
      while (iter.hasNext()) {
        try {
          // 获得 CacheRefResolver 集合，并遍历进行处理
          iter.next().resolveCacheRef();
          // 移除
          iter.remove();
        } catch (IncompleteElementException e) {
          // Cache ref is still missing a resource...
        }
      }
    }
  }

  private void parsePendingStatements() {
    // 获得 XMLStatementBuilder 集合，并遍历进行处理
    Collection<XMLStatementBuilder> incompleteStatements = configuration.getIncompleteStatements();
    synchronized (incompleteStatements) {
      Iterator<XMLStatementBuilder> iter = incompleteStatements.iterator();
      while (iter.hasNext()) {
        try {
          // 执行解析
          iter.next().parseStatementNode();
          // 移除
          iter.remove();
        } catch (IncompleteElementException e) {
          // Statement is still missing a resource...
        }
      }
    }
  }

  /**
   * #cacheRefElement(XNode context) 方法，解析 <cache-ref /> 节点
   * 例如： <cache-ref namespace="com.someone.application.data.SomeMapper"/>
   */
  private void cacheRefElement(XNode context) {
    if (context != null) {
      // <1> 获得指向的 namespace 名字，并添加到 configuration 的 cacheRefMap 中
      configuration.addCacheRef(builderAssistant.getCurrentNamespace(), context.getStringAttribute("namespace"));
      // <2> 创建 CacheRefResolver 对象，并执行解析
      CacheRefResolver cacheRefResolver = new CacheRefResolver(builderAssistant, context.getStringAttribute("namespace"));
      try {
        cacheRefResolver.resolveCacheRef();
      } catch (IncompleteElementException e) {
        // <3> 解析失败，添加到 configuration 的 incompleteCacheRefs 中
        configuration.addIncompleteCacheRef(cacheRefResolver);
      }
    }
  }

  /**
   * #cacheElement(XNode context) 方法，解析 cache /> 标签
   * -- 使用第三方缓存
   * <cache type="org.mybatis.caches.ehcache.EhcacheCache" />
   * -- 使用默认缓存
   * <cache eviction="FIFO" flushInterval="6000" readOnly="false" size="1024"/>
   *     eviction:缓存的回收策略：
   * 		• LRU – 最近最少使用的：移除最长时间不被使用的对象。
   * 		• FIFO – 先进先出：按对象进入缓存的顺序来移除它们。
   * 		• SOFT – 软引用：移除基于垃圾回收器状态和软引用规则的对象。
   * 		• WEAK – 弱引用：更积极地移除基于垃圾收集器状态和弱引用规则的对象。
   * 		• 默认的是 LRU。
   * 	flushInterval：缓存刷新间隔
   * 		缓存多长时间清空一次，默认不清空，设置一个毫秒值
   * 	readOnly:是否只读：
   * 		true：只读；mybatis认为所有从缓存中获取数据的操作都是只读操作，不会修改数据。
   * 				 mybatis为了加快获取速度，直接就会将数据在缓存中的引用交给用户。不安全，速度快
   * 		false：非只读：mybatis觉得获取的数据可能会被修改。
   * 				mybatis会利用序列化&反序列的技术克隆一份新的数据给你。安全，速度慢
   * 	size：缓存存放多少元素；
   * 	type=""：指定自定义缓存的全类名；
   * 			实现Cache接口即可；
   * -- 使用自定义缓存
   * <cache type="com.domain.something.MyCustomCache">
   *   <property name="cacheFile" value="/tmp/my-custom-cache.tmp"/>
   * </cache>
   *
   *    通过LRU PERPETUAL 能够获取到对应的类是因为
   * public Configuration() {
   *     .... 其他的映射关系
   *     typeAliasRegistry.registerAlias("PERPETUAL", PerpetualCache.class);
   *     typeAliasRegistry.registerAlias("FIFO", FifoCache.class);
   *     typeAliasRegistry.registerAlias("LRU", LruCache.class);
   *     typeAliasRegistry.registerAlias("SOFT", SoftCache.class);
   *     typeAliasRegistry.registerAlias("WEAK", WeakCache.class);
   */
  private void cacheElement(XNode context) {
    if (context != null) {
      // <1> 获得负责存储的 Cache 实现类
      String type = context.getStringAttribute("type", "PERPETUAL");
      Class<? extends Cache> typeClass = typeAliasRegistry.resolveAlias(type);
      // <2> 获得负责过期的 Cache 实现类
      String eviction = context.getStringAttribute("eviction", "LRU");
      Class<? extends Cache> evictionClass = typeAliasRegistry.resolveAlias(eviction);
      // <3> 获得 flushInterval、size、readWrite、blocking 属性
      Long flushInterval = context.getLongAttribute("flushInterval");
      Integer size = context.getIntAttribute("size");
      boolean readWrite = !context.getBooleanAttribute("readOnly", false);
      boolean blocking = context.getBooleanAttribute("blocking", false);
      // <4> 获得 Properties 属性
      Properties props = context.getChildrenAsProperties();
      // <5> 创建 Cache 对象
      builderAssistant.useNewCache(typeClass, evictionClass, flushInterval, size, readWrite, blocking, props);
    }
  }

  private void parameterMapElement(List<XNode> list) {
    for (XNode parameterMapNode : list) {
      String id = parameterMapNode.getStringAttribute("id");
      String type = parameterMapNode.getStringAttribute("type");
      Class<?> parameterClass = resolveClass(type);
      List<XNode> parameterNodes = parameterMapNode.evalNodes("parameter");
      List<ParameterMapping> parameterMappings = new ArrayList<>();
      for (XNode parameterNode : parameterNodes) {
        String property = parameterNode.getStringAttribute("property");
        String javaType = parameterNode.getStringAttribute("javaType");
        String jdbcType = parameterNode.getStringAttribute("jdbcType");
        String resultMap = parameterNode.getStringAttribute("resultMap");
        String mode = parameterNode.getStringAttribute("mode");
        String typeHandler = parameterNode.getStringAttribute("typeHandler");
        Integer numericScale = parameterNode.getIntAttribute("numericScale");
        ParameterMode modeEnum = resolveParameterMode(mode);
        Class<?> javaTypeClass = resolveClass(javaType);
        JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
        Class<? extends TypeHandler<?>> typeHandlerClass = resolveClass(typeHandler);
        ParameterMapping parameterMapping = builderAssistant.buildParameterMapping(parameterClass, property, javaTypeClass, jdbcTypeEnum, resultMap, modeEnum, typeHandlerClass, numericScale);
        parameterMappings.add(parameterMapping);
      }
      builderAssistant.addParameterMap(id, parameterClass, parameterMappings);
    }
  }

  /**
   * 解析 <resultMap /> 节点们
   *    http://static.iocoder.cn/images/MyBatis/2020_02_13/03.png
   */
  private void resultMapElements(List<XNode> list) throws Exception {
    // 遍历 <resultMap /> 节点们
    for (XNode resultMapNode : list) {
      try {
        // 处理单个 <resultMap /> 节点
        resultMapElement(resultMapNode);
      } catch (IncompleteElementException e) {
        // ignore, it will be retried
      }
    }
  }

  /**
   * 解析 <resultMap /> 节点
   * @param resultMapNode
   * @return
   * @throws Exception
   */
  private ResultMap resultMapElement(XNode resultMapNode) throws Exception {
    return resultMapElement(resultMapNode, Collections.<ResultMapping> emptyList(), null);
  }

  /**
   * 解析 <resultMap /> 节点们
   <resultMap id="BaseResultMap" type="com.smile.skype.learnmybatis.bean.Department" >
      <id column="id" property="id"/>
      <result column="dept_name" property="departmentName"/>
   </resultMap>

   <!-- 使用association定义关联的单个对象的封装规则 -->
   <resultMap id="MyDiffEmp2" type="com.smile.skype.learnmybatis.bean.Employee">
          <id column="id" property="id"/>
        <!-- association标签可以指定联合查询的javaBean对象   property 指定哪个属性是联合的对象 javaType：指定联合对象的类型,不能省略 -->
          <association property="dept" javaType="com.smile.skype.learnmybatis.bean.Department">
                 <id column="did" property="id"/>
                  <result column="dept_name" property="departmentName" />
          </association>
         <!-- association 定义关联对象的封装规则  select 表示当前属性是调用select指定的方法查询的结果 column: 表示将哪一列的值传给这个方法
         流程：使用select指定的方法,传入column指定列参数的值查询出对象,并封装给property指定的属性 -->
         <association property="dept"
                select="com.smile.skype.learnmybatis.dao.DepartmentMapper.getDeptById"
                column="d_id">
   </resultMap>

   关于resultMap更详细的,可以看 https://mybatis.org/mybatis-3/zh/sqlmap-xml.html#%E9%AB%98%E7%BA%A7%E7%BB%93%E6%9E%9C%E6%98%A0%E5%B0%84
   结果映射（resultMap）
     constructor - 用于在实例化类时，注入结果到构造方法中
         idArg - ID 参数；标记出作为 ID 的结果可以帮助提高整体性能
         arg - 将被注入到构造方法的一个普通结果
     id – 一个 ID 结果；标记出作为 ID 的结果可以帮助提高整体性能
     result – 注入到字段或 JavaBean 属性的普通结果
     association – 一个复杂类型的关联；许多结果将包装成这种类型
          嵌套结果映射 – 关联可以是 resultMap 元素，或是对其它结果映射的引用
     collection – 一个复杂类型的集合
          嵌套结果映射 – 集合可以是 resultMap 元素，或是对其它结果映射的引用
     discriminator – 使用结果值来决定使用哪个 resultMap
          case – 基于某些值的结果映射
            嵌套结果映射 – case 也是一个结果映射，因此具有相同的结构和元素；或者引用其它的结果映射
   */
  private ResultMap resultMapElement(XNode resultMapNode, List<ResultMapping> additionalResultMappings, Class<?> enclosingType) throws Exception {
    ErrorContext.instance().activity("processing " + resultMapNode.getValueBasedIdentifier());
    // <1> 获得 id 属性
    String id = resultMapNode.getStringAttribute("id",
        resultMapNode.getValueBasedIdentifier());
    // <1> 获得 type 属性
    String type = resultMapNode.getStringAttribute("type",
        resultMapNode.getStringAttribute("ofType",
            resultMapNode.getStringAttribute("resultType",
                resultMapNode.getStringAttribute("javaType"))));
    // <1> 获得 extends 属性   继承
    String extend = resultMapNode.getStringAttribute("extends");
    // <1> 获得 autoMapping 属性
    Boolean autoMapping = resultMapNode.getBooleanAttribute("autoMapping");
    // <1> 解析 type 对应的类
    Class<?> typeClass = resolveClass(type);
    if (typeClass == null) {
      // 别名注册中如果没找到entity的class对象，
      typeClass = inheritEnclosingType(resultMapNode, enclosingType);
    }
    Discriminator discriminator = null;
    // <2> 创建 ResultMapping 集合
    List<ResultMapping> resultMappings = new ArrayList<>();
    resultMappings.addAll(additionalResultMappings);
    // <2> 遍历 <resultMap /> 的子节点
    List<XNode> resultChildren = resultMapNode.getChildren();
    for (XNode resultChild : resultChildren) {
      // <2.1> 处理 <constructor /> 节点
      if ("constructor".equals(resultChild.getName())) {
        processConstructorElement(resultChild, typeClass, resultMappings);
      } else if ("discriminator".equals(resultChild.getName())) {
        // <2.2> 处理 <discriminator /> 节点
        discriminator = processDiscriminatorElement(resultChild, typeClass, resultMappings);
      } else {
        // <2.3> 处理其它节点
        List<ResultFlag> flags = new ArrayList<>();
        if ("id".equals(resultChild.getName())) {
          flags.add(ResultFlag.ID);
        }
        // 将当前子节点构建成 ResultMapping 对象，并添加到 resultMappings 中
        resultMappings.add(buildResultMappingFromContext(resultChild, typeClass, flags));
      }
    }
    // <3> 创建 ResultMapResolver 对象，执行解析
    ResultMapResolver resultMapResolver = new ResultMapResolver(builderAssistant, id, typeClass, extend, discriminator, resultMappings, autoMapping);
    try {
      return resultMapResolver.resolve();
    } catch (IncompleteElementException  e) {
      /**
       * <4> 解析失败，添加到 configuration 中 说明有依赖的信息不全，
       * 所以调用 Configuration#addIncompleteResultMap(ResultMapResolver resultMapResolver) 方法，
       * 添加到 Configuration 的 incompleteResultMaps 中
        */
      configuration.addIncompleteResultMap(resultMapResolver);
      throw e;
    }
  }

  /**
   * 见得比较少,先跳过
   * @return
   */
  protected Class<?> inheritEnclosingType(XNode resultMapNode, Class<?> enclosingType) {
    if ("association".equals(resultMapNode.getName()) && resultMapNode.getStringAttribute("resultMap") == null) {
      String property = resultMapNode.getStringAttribute("property");
      if (property != null && enclosingType != null) {
        MetaClass metaResultType = MetaClass.forClass(enclosingType, configuration.getReflectorFactory());
        return metaResultType.getSetterType(property);
      }
    } else if ("case".equals(resultMapNode.getName()) && resultMapNode.getStringAttribute("resultMap") == null) {
      return enclosingType;
    }
    return null;
  }

  /**
   *    * #processConstructorElement(XNode resultChild, Class<?> resultType, List<ResultMapping> resultMappings) 方法，处理 <constructor /> 节点
   *    * https://mybatis.org/mybatis-3/zh/sqlmap-xml.html#%E9%AB%98%E7%BA%A7%E7%BB%93%E6%9E%9C%E6%98%A0%E5%B0%84
   <constructor>
       <idArg column="id" javaType="int" name="id" />
       <arg column="age" javaType="_int" name="age" />
       <arg column="username" javaType="String" name="username" />
   </constructor>
   *    *
   * @param resultChild  constructor 标签
   * @param resultType   当前resultMap对应的type类
   * @param resultMappings
   */
  private void processConstructorElement(XNode resultChild, Class<?> resultType, List<ResultMapping> resultMappings) throws Exception {
    // <1> 遍历 <constructor /> 的子节点们
    List<XNode> argChildren = resultChild.getChildren();
    for (XNode argChild : argChildren) {
      List<ResultFlag> flags = new ArrayList<>();
      // <2> 获得 ResultFlag 集合
      flags.add(ResultFlag.CONSTRUCTOR);
      if ("idArg".equals(argChild.getName())) {
        flags.add(ResultFlag.ID);
      }
      // <3> 将当前子节点构建成 ResultMapping 对象，并添加到 resultMappings 中
      resultMappings.add(buildResultMappingFromContext(argChild, resultType, flags));
    }
  }

  /**
   * <discriminator javaType="int" column="draft">
   *       <case value="1" resultType="DraftPost"/>
   *     </discriminator>
   * discriminator – 使用结果值来决定使用哪个 resultMap
   *    case – 基于某些值的结果映射
   *    嵌套结果映射 – case 也是一个结果映射，因此具有相同的结构和元素；或者引用其它的结果映射
   */
  private Discriminator processDiscriminatorElement(XNode context, Class<?> resultType, List<ResultMapping> resultMappings) throws Exception {
    // <1> 解析各种属性
    String column = context.getStringAttribute("column");
    String javaType = context.getStringAttribute("javaType");
    String jdbcType = context.getStringAttribute("jdbcType");
    String typeHandler = context.getStringAttribute("typeHandler");
    // <1> 解析各种属性对应的类
    Class<?> javaTypeClass = resolveClass(javaType);
    Class<? extends TypeHandler<?>> typeHandlerClass = resolveClass(typeHandler);
    JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
    // <2> 遍历 <discriminator /> 的子节点，解析成 discriminatorMap 集合
    Map<String, String> discriminatorMap = new HashMap<>();
    for (XNode caseChild : context.getChildren()) {
      String value = caseChild.getStringAttribute("value");
      // 如果是内嵌的resultMap,则调用processNestedResultMappings
      String resultMap = caseChild.getStringAttribute("resultMap", processNestedResultMappings(caseChild, resultMappings, resultType));
      discriminatorMap.put(value, resultMap);
    }
    // <3> 创建 Discriminator 对象
    return builderAssistant.buildDiscriminator(resultType, column, javaTypeClass, jdbcTypeEnum, typeHandlerClass, discriminatorMap);
  }

  /**
   *     <sql id="insertColumn">
   *         <if test="_databaseId=='oracle'">employee_id,last_name,email</if>
   *         <if test="_databaseId=='mysql'">last_name,email,gender,d_id</if>
   *     </sql>
   * @param list  解析<sql />节点们
   */
  private void sqlElement(List<XNode> list) {
    if (configuration.getDatabaseId() != null) {
      // 先加载带databaseId的
      sqlElement(list, configuration.getDatabaseId());
    }
    sqlElement(list, null);
  }

  /**
   * 解析 /mapper/sql 节点
   * @param list
   * @param requiredDatabaseId
   */
  private void sqlElement(List<XNode> list, String requiredDatabaseId) {
    // <1> 遍历所有 <sql /> 节点
    for (XNode context : list) {
      // <2> 获得 databaseId 属性
      String databaseId = context.getStringAttribute("databaseId");
      // <3> 获得完整的 id 属性，格式为 `${namespace}.${id}`
      String id = context.getStringAttribute("id");
      id = builderAssistant.applyCurrentNamespace(id, false);
      // <4> 判断 databaseId 是否匹配
      if (databaseIdMatchesCurrent(id, databaseId, requiredDatabaseId)) {
        // <5> 添加到 sqlFragments 中
        sqlFragments.put(id, context);
      }
    }
  }

  /**
   * 判断 databaseId 是否匹配
   * @param id  ${namespace}.${id}
   * @param databaseId 当前<sql> 标签上面的配置
   * @param requiredDatabaseId 需求的类型
   * @return
   */
  private boolean databaseIdMatchesCurrent(String id, String databaseId, String requiredDatabaseId) {
    // 如果不匹配，则返回 false
    if (requiredDatabaseId != null) {
      if (!requiredDatabaseId.equals(databaseId)) {
        return false;
      }
    } else {
      // 如果未设置 requiredDatabaseId ，但是 databaseId 存在，说明还是不匹配，则返回 false
      if (databaseId != null) {
        return false;
      }
      // skip this fragment if there is a previous one with a not null databaseId
      // 判断是否已经存在
      if (this.sqlFragments.containsKey(id)) {
        XNode context = this.sqlFragments.get(id);
        // 若存在，则判断原有的 sqlFragment 是否 databaseId 为空。因为，当前 databaseId 为空，这样两者才能匹配
        if (context.getStringAttribute("databaseId") != null) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * 讲当前节点构建成ResultMapping对象
   *
   *     <resultMap id="BaseResultMap" type="com.smile.skype.learnmybatis.bean.Department" >
   *         <id column="id" property="id"/>
   *         <result column="dept_name" property="departmentName"/>
   *     </resultMap>
   * @param context 代表的是id或者是result标签
   * @param resultType resultMap中type对应的类
   * @param flags ID或者CONSTRUCTOR
   */
  private ResultMapping buildResultMappingFromContext(XNode context, Class<?> resultType, List<ResultFlag> flags) throws Exception {
    // <1> 获得各种属性
    String property;
    if (flags.contains(ResultFlag.CONSTRUCTOR)) {
      property = context.getStringAttribute("name");
    } else {
      property = context.getStringAttribute("property");
    }
    String column = context.getStringAttribute("column");
    String javaType = context.getStringAttribute("javaType");
    String jdbcType = context.getStringAttribute("jdbcType");
    String nestedSelect = context.getStringAttribute("select");
    String nestedResultMap = context.getStringAttribute("resultMap",
        processNestedResultMappings(context, Collections.<ResultMapping> emptyList(), resultType));
    String notNullColumn = context.getStringAttribute("notNullColumn");
    String columnPrefix = context.getStringAttribute("columnPrefix");
    String typeHandler = context.getStringAttribute("typeHandler");
    String resultSet = context.getStringAttribute("resultSet");
    String foreignColumn = context.getStringAttribute("foreignColumn");
    boolean lazy = "lazy".equals(context.getStringAttribute("fetchType", configuration.isLazyLoadingEnabled() ? "lazy" : "eager"));
    // <1> 获得各种属性对应的类
    Class<?> javaTypeClass = resolveClass(javaType);
    Class<? extends TypeHandler<?>> typeHandlerClass = resolveClass(typeHandler);
    JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
    // <2> 构建 ResultMapping 对象
    return builderAssistant.buildResultMapping(resultType, property, column, javaTypeClass, jdbcTypeEnum, nestedSelect, nestedResultMap, notNullColumn, columnPrefix, typeHandlerClass, flags, resultSet, foreignColumn, lazy);
  }

  /**
   * 处理内嵌的 ResultMap 的情况
   *    <association property="dept"
   *                      select="com.smile.skype.learnmybatis.dao.DepartmentMapper.getDeptById"
   *                      column="d_id">
   *         </association>
   */
  private String processNestedResultMappings(XNode context, List<ResultMapping> resultMappings, Class<?> enclosingType) throws Exception {
    if ("association".equals(context.getName())
        || "collection".equals(context.getName())
        || "case".equals(context.getName())) {
      if (context.getStringAttribute("select") == null) {
        // 验证collection标签是否有resultType和resultMap属性
        validateCollection(context, enclosingType);
        // 解析，并返回 ResultMap
        ResultMap resultMap = resultMapElement(context, resultMappings, enclosingType);
        return resultMap.getId();
      }
    }
    return null;
  }

  /**
   * 验证collection集合是否有对应的resultType和resultMap
   * @param context
   * @param enclosingType
   */
  protected void validateCollection(XNode context, Class<?> enclosingType) {
    if ("collection".equals(context.getName()) && context.getStringAttribute("resultMap") == null
      && context.getStringAttribute("resultType") == null) {
      MetaClass metaResultType = MetaClass.forClass(enclosingType, configuration.getReflectorFactory());
      String property = context.getStringAttribute("property");
      if (!metaResultType.hasSetter(property)) {
        throw new BuilderException(
          "Ambiguous collection type for property '" + property + "'. You must specify 'resultType' or 'resultMap'.");
      }
    }
  }

  /**
   * 绑定Mapper
   */
  private void bindMapperForNamespace() {
    String namespace = builderAssistant.getCurrentNamespace();
    if (namespace != null) {
      // <1> 获得 Mapper 映射配置文件对应的 Mapper 接口，实际上类名就是 namespace
      Class<?> boundType = null;
      try {
        boundType = Resources.classForName(namespace);
      } catch (ClassNotFoundException e) {
        //ignore, bound type is not required
      }
      if (boundType != null) {
        // <2> 不存在该 Mapper 接口，则进行添加
        if (!configuration.hasMapper(boundType)) {
          // Spring may not know the real resource name so we set a flag
          // to prevent loading again this resource from the mapper interface
          // look at MapperAnnotationBuilder#loadXmlResource
          // <3> 标记 namespace 已经添加，避免 MapperAnnotationBuilder#loadXmlResource(...) 重复加载
          configuration.addLoadedResource("namespace:" + namespace);
          // <4> 添加到 configuration 中
          configuration.addMapper(boundType);
        }
      }
    }
  }

}
