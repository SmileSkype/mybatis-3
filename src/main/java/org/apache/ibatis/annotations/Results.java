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
 * @author Clinton Begin
 * 结果的注解  对应 XML 标签为 <resultMap />
 * @Results(value ={
 *             @Result(id=true, property="id",column="id",javaType=Integer.class,jdbcType=JdbcType.INTEGER),
 *             @Result(property="title",column="title",javaType=String.class,jdbcType=JdbcType.VARCHAR),
 *             @Result(property="date",column="date",javaType=String.class,jdbcType=JdbcType.VARCHAR),
 *             @Result(property="authername",column="authername",javaType=String.class,jdbcType=JdbcType.VARCHAR),
 *             @Result(property="content",column="content",javaType=String.class,jdbcType=JdbcType.VARCHAR),
 *             })
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Results {
  /**
   * The name of the result map.
   */
  String id() default "";
  /**
   * @return {@link Result} 数组
   */
  Result[] value() default {};
}
