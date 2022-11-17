/**
 *    Copyright 2009-2015 the original author or authors.
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
/**
 * Base package for languages.
 * 根据用户传入的实参,解析映射文件中定义的动态SQL节点,并形成数据库可执行的SQL语句
 * 之后会处理SQL语句中的占位符,绑定用户传入的实参
 *      http://www.mybatis.org/mybatis-3/zh/dynamic-sql.html
 */
package org.apache.ibatis.scripting;
