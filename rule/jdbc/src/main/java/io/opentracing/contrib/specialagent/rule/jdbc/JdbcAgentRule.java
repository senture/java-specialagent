/* Copyright 2018 The OpenTracing Authors
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

package io.opentracing.contrib.specialagent.rule.jdbc;

import static net.bytebuddy.matcher.ElementMatchers.*;

import java.sql.Connection;
import java.util.Arrays;
import java.util.Properties;

import io.opentracing.contrib.specialagent.AgentRule;
import io.opentracing.contrib.specialagent.EarlyReturnException;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Identified.Extendable;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import net.bytebuddy.matcher.ElementMatcher.Junction;
import net.bytebuddy.utility.JavaModule;

public class JdbcAgentRule extends AgentRule {
  @Override
  public Iterable<? extends AgentBuilder> buildAgent(final AgentBuilder builder) throws Exception {
    final Junction<TypeDescription> driverJunction = named("java.sql.DriverManager").or(hasSuperType(named("java.sql.Driver")).and(not(named("io.opentracing.contrib.jdbc.TracingDriver"))));
    final Extendable enter = builder.type(driverJunction)
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(DriverEnter.class).on(not(isAbstract()).and(named("connect").and(takesArguments(String.class, Properties.class)))));
        }})
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(DriverManagerEnter.class).on(isPrivate().and(isStatic()).and(named("isDriverAllowed")).and(takesArgument(1, Class.class))));
        }});

    final Extendable exit = builder.type(driverJunction)
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(DriverManagerExit.class).on(isPrivate().and(isStatic()).and(named("isDriverAllowed")).and(takesArgument(1, Class.class))));
        }})
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(DriverExit.class).on(not(isAbstract()).and(named("connect").and(takesArguments(String.class, Properties.class)))));
        }});

    return Arrays.asList(enter, exit);
  }

  public static class DriverManagerEnter {
    @Advice.OnMethodEnter
    public static void enter(final @Advice.Origin String origin, final @Advice.Argument(value = 1) Class<?> caller) throws Exception {
      if (isEnabled(origin))
        JdbcAgentIntercept.isDriverAllowed(caller);
    }
  }

  public static class DriverManagerExit {
    @SuppressWarnings("unused")
    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void exit(@Advice.Return(readOnly = false, typing = Typing.DYNAMIC) Boolean returned, @Advice.Thrown(readOnly = false, typing = Typing.DYNAMIC) Throwable thrown) {
      if (thrown instanceof EarlyReturnException) {
        thrown = null;
        returned = Boolean.TRUE;
      }
    }
  }

  public static class DriverEnter {
    @Advice.OnMethodEnter
    public static void enter(final @Advice.Origin String origin, final @Advice.Argument(value = 0) String url, final @Advice.Argument(value = 1) Properties info) throws Exception {
      if (!isEnabled(origin))
        return;

      final Connection connection = JdbcAgentIntercept.connect(url, info);
      if (connection != null)
        throw new EarlyReturnException(connection);
    }
  }

  public static class DriverExit {
    @SuppressWarnings("unused")
    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void exit(@Advice.Return(readOnly = false, typing = Typing.DYNAMIC) Object returned, @Advice.Thrown(readOnly = false, typing = Typing.DYNAMIC) Throwable thrown) throws Exception {
      if (thrown instanceof EarlyReturnException) {
        returned = ((EarlyReturnException)thrown).getReturnValue();
        thrown = null;
      }
    }
  }
}