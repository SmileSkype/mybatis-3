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
 * Base package for loading results into beans
 * 实现延迟加载功能
 * 延迟加载类图：http://static.iocoder.cn/images/MyBatis/2020_03_12/01.png
 * 从类图，我们发现，延迟加载的功能，是通过动态代理实现的。也就是说，通过拦截指定方法，执行数据加载，从而实现延迟加载。
 * 并且，MyBatis 提供了 Cglib 和 Javassist 两种动态代理的创建方式
 */
package org.apache.ibatis.executor.loader;
