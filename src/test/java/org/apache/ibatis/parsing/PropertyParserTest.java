/**
 *    Copyright 2009-2018 the original author or authors.
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
package org.apache.ibatis.parsing;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Properties;

/**
 * 主要内容
 * 1、设置默认值  props.setProperty(PropertyParser.KEY_ENABLE_DEFAULT_VALUE, "true");
 * 2、设置分隔符  分割符默认是 ：  可以进行修改： props.setProperty(PropertyParser.KEY_DEFAULT_VALUE_SEPARATOR, "?:");
 * 所以在有默认值的情况下：
 *    ${key:aaa}  默认值是aaas 如果在Properties中没有找到对应的值,会用默认值代替,如果找到了对应的值,则使用对应的值
 * 在不设置默认值时,会将 key:aaa 当作一个 整体 然后在Properties中找对应的value
 *
 *
 */
public class PropertyParserTest {

  /**
   * Assertions.assertThat 断言
   *  开源的测试库
   */
  @Test
  public void replaceToVariableValue() {
    Properties props = new Properties();
    props.setProperty(PropertyParser.KEY_ENABLE_DEFAULT_VALUE, "true");
    props.setProperty("key", "value");
    props.setProperty("tableName", "members");
    props.setProperty("orderColumn", "member_id");
    props.setProperty("a:b", "c");
    Assertions.assertThat(PropertyParser.parse("${key}", props)).isEqualTo("value");
    Assertions.assertThat(PropertyParser.parse("${smile:默认值}", props)).isEqualTo("默认值");
    Assertions.assertThat(PropertyParser.parse("${key:aaaa}", props)).isEqualTo("value");
    Assertions.assertThat(PropertyParser.parse("SELECT * FROM ${tableName:users} ORDER BY ${orderColumn:id}", props)).isEqualTo("SELECT * FROM members ORDER BY member_id");

    //
    props.setProperty(PropertyParser.KEY_ENABLE_DEFAULT_VALUE, "false");

    Assertions.assertThat(PropertyParser.parse("${a:b}", props)).isEqualTo("c");
//    Assertions.assertThat(PropertyParser.parse("${key:aaaa}", props)).isEqualTo("c");

    props.remove(PropertyParser.KEY_ENABLE_DEFAULT_VALUE);
    Assertions.assertThat(PropertyParser.parse("${a:b}", props)).isEqualTo("c");

  }

  @Test
  public void notReplace() {
    Properties props = new Properties();
    props.setProperty(PropertyParser.KEY_ENABLE_DEFAULT_VALUE, "true");
    Assertions.assertThat(PropertyParser.parse("${key}", props)).isEqualTo("${key}");
    Assertions.assertThat(PropertyParser.parse("${key}", null)).isEqualTo("${key}");

    props.setProperty(PropertyParser.KEY_ENABLE_DEFAULT_VALUE, "false");
    Assertions.assertThat(PropertyParser.parse("${a:b}", props)).isEqualTo("${a:b}");

    props.remove(PropertyParser.KEY_ENABLE_DEFAULT_VALUE);
    Assertions.assertThat(PropertyParser.parse("${a:b}", props)).isEqualTo("${a:b}");

  }

  @Test
  public void applyDefaultValue() {
    Properties props = new Properties();
    props.setProperty(PropertyParser.KEY_ENABLE_DEFAULT_VALUE, "true");
    Assertions.assertThat(PropertyParser.parse("${key:default}", props)).isEqualTo("default");
    Assertions.assertThat(PropertyParser.parse("SELECT * FROM ${tableName:users} ORDER BY ${orderColumn:id}", props)).isEqualTo("SELECT * FROM users ORDER BY id");
    Assertions.assertThat(PropertyParser.parse("${key:}", props)).isEmpty();
    Assertions.assertThat(PropertyParser.parse("${key: }", props)).isEqualTo(" ");
    Assertions.assertThat(PropertyParser.parse("${key::}", props)).isEqualTo(":");
  }

  @Test
  public void applyCustomSeparator() {
    Properties props = new Properties();
    props.setProperty(PropertyParser.KEY_ENABLE_DEFAULT_VALUE, "true");
    props.setProperty(PropertyParser.KEY_DEFAULT_VALUE_SEPARATOR, "?:");
    Assertions.assertThat(PropertyParser.parse("${key?:default}", props)).isEqualTo("default");
    Assertions.assertThat(PropertyParser.parse("SELECT * FROM ${schema?:prod}.${tableName == null ? 'users' : tableName} ORDER BY ${orderColumn}", props)).isEqualTo("SELECT * FROM prod.${tableName == null ? 'users' : tableName} ORDER BY ${orderColumn}");
    Assertions.assertThat(PropertyParser.parse("${key?:}", props)).isEmpty();
    Assertions.assertThat(PropertyParser.parse("${key?: }", props)).isEqualTo(" ");
    Assertions.assertThat(PropertyParser.parse("${key?::}", props)).isEqualTo(":");
  }

}
