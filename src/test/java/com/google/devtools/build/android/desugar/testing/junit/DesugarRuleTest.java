/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.devtools.build.android.desugar.testing.junit;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.devtools.build.lib.testutil.MoreAsserts.assertThrows;

import com.google.devtools.build.android.desugar.testing.junit.RuntimeMethodHandle.MemberUseContext;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import javax.inject.Inject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

/** The test for {@link DesugarRule}. */
@RunWith(JUnit4.class)
public final class DesugarRuleTest {

  @Rule
  public final DesugarRule desugarRule =
      DesugarRule.builder(this, MethodHandles.lookup())
          .addInputs(Paths.get(System.getProperty("input_jar")))
          .enableIterativeTransformation(3)
          .setWorkingJavaPackage("com.google.devtools.build.android.desugar.testing.junit")
          .build();

  @Inject
  @DynamicClassLiteral("DesugarRuleTestTarget$InterfaceSubjectToDesugar")
  private Class<?> interfaceSubjectToDesugarRound1;

  @Inject
  @DynamicClassLiteral(value = "DesugarRuleTestTarget$InterfaceSubjectToDesugar", round = 2)
  private Class<?> interfaceSubjectToDesugarRound2;

  @Inject
  @DynamicClassLiteral("DesugarRuleTestTarget$InterfaceSubjectToDesugar")
  private Class<?> interfaceSubjectToDesugarFromSimpleClassName;

  @Inject
  @DynamicClassLiteral(
      "com.google.devtools.build.android.desugar.testing.junit.DesugarRuleTestTarget$InterfaceSubjectToDesugar")
  private Class<?> interfaceSubjectToDesugarFromQualifiedClassName;

  @Inject
  @DynamicClassLiteral("DesugarRuleTestTarget$InterfaceSubjectToDesugar$$CC")
  private Class<?> interfaceSubjectToDesugarCompanionClassRound1;

  @Inject
  @DynamicClassLiteral(value = "DesugarRuleTestTarget$InterfaceSubjectToDesugar$$CC", round = 2)
  private Class<?> interfaceSubjectToDesugarCompanionClassRound2;

  @Inject
  @RuntimeZipEntry(value = "DesugarRuleTestTarget$InterfaceSubjectToDesugar$$CC.class", round = 1)
  private ZipEntry interfaceSubjectToDesugarZipEntryRound1;

  @Inject
  @RuntimeZipEntry(value = "DesugarRuleTestTarget$InterfaceSubjectToDesugar$$CC.class", round = 2)
  private ZipEntry interfaceSubjectToDesugarZipEntryRound2;

  @Inject
  @AsmNode(className = "DesugarRuleTestTarget")
  private ClassNode desugarRuleTestTargetClassNode;

  @Inject
  @AsmNode(className = "DesugarRuleTestTarget$Alpha", memberName = "twoIntSum")
  private MethodNode twoIntSum;

  @Inject
  @AsmNode(
      className = "DesugarRuleTestTarget$Alpha",
      memberName = "multiplier",
      memberDescriptor = "J")
  private FieldNode multiplier;

  @Inject
  @RuntimeMethodHandle(className = "DesugarRuleTestTarget$Alpha", memberName = "twoIntSum")
  private MethodHandle twoIntSumMH;

  @Inject
  @RuntimeMethodHandle(className = "DesugarRuleTestTarget$Alpha", memberName = "<init>")
  private MethodHandle alphaConstructor;

  @Inject
  @RuntimeMethodHandle(
      className = "DesugarRuleTestTarget$Alpha",
      memberName = "linearLongTransform")
  private MethodHandle linearLongTransform;

  @Inject
  @RuntimeMethodHandle(
      className = "DesugarRuleTestTarget$Alpha",
      memberName = "multiplier",
      usage = MemberUseContext.FIELD_GETTER)
  private MethodHandle alphaMultiplierGetter;

  @Inject
  @RuntimeMethodHandle(
      className = "DesugarRuleTestTarget$Alpha",
      memberName = "multiplier",
      usage = MemberUseContext.FIELD_SETTER)
  private MethodHandle alphaMultiplierSetter;

  @Test
  public void staticMethodsAreMovedFromOriginatingClass() {
    assertThrows(
        NoSuchMethodException.class,
        () -> interfaceSubjectToDesugarRound1.getDeclaredMethod("staticMethod"));
  }

  @Test
  public void staticMethodsAreMovedFromOriginatingClass_desugarTwice() {
    assertThrows(
        NoSuchMethodException.class,
        () -> interfaceSubjectToDesugarRound2.getDeclaredMethod("staticMethod"));
  }

  @Test
  public void staticMethodsAreMovedToCompanionClass() {
    assertThat(
            Arrays.stream(interfaceSubjectToDesugarCompanionClassRound1.getDeclaredMethods())
                .map(Method::getName))
        .contains("staticMethod$$STATIC$$");
  }

  @Test
  public void innerClasses() {
    assertThat(
            desugarRuleTestTargetClassNode.innerClasses.stream().map(classNode -> classNode.name))
        .contains(
            "com/google/devtools/build/android/desugar/testing/junit/DesugarRuleTestTarget$InterfaceSubjectToDesugar");
  }

  @Test
  public void idempotencyOperation() {
    assertThat(interfaceSubjectToDesugarZipEntryRound1.getCrc())
        .isEqualTo(interfaceSubjectToDesugarZipEntryRound2.getCrc());
  }

  @Test
  public void classLoaders_sameInstanceInSameRound() {
    assertThat(interfaceSubjectToDesugarRound1.getClassLoader())
        .isSameInstanceAs(interfaceSubjectToDesugarCompanionClassRound1.getClassLoader());
    assertThat(interfaceSubjectToDesugarRound2.getClassLoader())
        .isSameInstanceAs(interfaceSubjectToDesugarCompanionClassRound2.getClassLoader());
  }

  @Test
  public void classLiterals_requestedWithFullQualifiedClassName() {
    assertThat(interfaceSubjectToDesugarFromQualifiedClassName)
        .isSameInstanceAs(interfaceSubjectToDesugarFromSimpleClassName);
  }

  @Test
  public void injectFieldNodes() {
    assertThat(twoIntSum.desc).isEqualTo("(II)I");
  }

  @Test
  public void injectMethodNodes() {
    assertThat(multiplier.desc).isEqualTo("J");
  }

  @Test
  public void invokeStaticMethodHandle() throws Throwable {
    int result = (int) twoIntSumMH.invoke(1, 2);
    assertThat(result).isEqualTo(3);
  }

  @Test
  public void invokeVirtualMethodHandle() throws Throwable {
    long result = (long) linearLongTransform.invoke(alphaConstructor.invoke(1000, 2), 3L);
    assertThat(result).isEqualTo(3002);
  }

  @Test
  public void invokeFieldGetter() throws Throwable {
    Object alpha = alphaConstructor.invoke(1000, 2);
    long result = (long) alphaMultiplierGetter.invoke(alpha);
    assertThat(result).isEqualTo(1000);
  }

  @Test
  public void invokeFieldSetter() throws Throwable {
    Object alpha = alphaConstructor.invoke(1000, 2);
    alphaMultiplierSetter.invoke(alpha, 1111);

    long result = (long) alphaMultiplierGetter.invoke(alpha);
    assertThat(result).isEqualTo(1111);
  }
}

