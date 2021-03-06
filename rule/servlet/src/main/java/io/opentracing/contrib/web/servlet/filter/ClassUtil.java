/* Copyright 2019 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentracing.contrib.web.servlet.filter;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class ClassUtil {
  public static Method getMethod(final Class<?> cls, final String name, final Class<?> ... parameterTypes) {
    try {
      final Method method = cls.getMethod(name, parameterTypes);
      return Modifier.isAbstract(method.getModifiers()) ? null : method;
    }
    catch (final NoSuchMethodException e) {
      return null;
    }
  }

  public static boolean invoke(final Object[] returned, final Object obj, final Method method, final Object ... args) {
    if (method == null)
      return false;

    try {
      returned[0] = method.invoke(obj, args);
      return true;
    }
    catch (final IllegalAccessException | InvocationTargetException e) {
      return false;
    }
  }

  private ClassUtil() {
  }
}