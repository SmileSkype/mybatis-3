/**
 *    Copyright 2009-2016 the original author or authors.
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
package org.apache.ibatis.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Jeff Butler
 * 使用的结果集的注解
 * @SelectProvider(type = BlogSqlProvider.class, method = "getSqlByTitle")
 *     @ResultMap(value = "sqlBlogsMap")
 *     // 这里调用resultMap，这个是SQL配置文件中的,必须该SQL配置文件与本接口有相同的全限定名
 *     // 注意文件中的namespace路径必须是使用@resultMap的类路径
 *     public List<Blog> getBlogByTitle(@Param("title")String title);
 *
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ResultMap {
  /**
   * @return 结果集,需要在对应的XML有相应的内容
   */
  String[] value();
}
