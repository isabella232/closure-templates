/*
 * Copyright 2009 Google Inc.
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

package com.google.template.soy.bidifunctions;

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.jbcsrc.restricted.JbcSrcPluginContext;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.SoyJbcSrcFunction;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyLibraryAssistedJsSrcFunction;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.SoyPySrcFunction;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * Soy function that inserts a bidi mark character (LRM or RLM) for the current global bidi
 * directionality.
 *
 */
@Singleton
final class BidiMarkFunction
    implements SoyJavaFunction,
        SoyLibraryAssistedJsSrcFunction,
        SoyPySrcFunction,
        SoyJbcSrcFunction {

  /** Provider for the current bidi global directionality. */
  private final Provider<BidiGlobalDir> bidiGlobalDirProvider;

  /** @param bidiGlobalDirProvider Provider for the current bidi global directionality. */
  @Inject
  BidiMarkFunction(Provider<BidiGlobalDir> bidiGlobalDirProvider) {
    this.bidiGlobalDirProvider = bidiGlobalDirProvider;
  }

  @Override
  public String getName() {
    return "bidiMark";
  }

  @Override
  public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(0);
  }

  @Override
  public SoyValue computeForJava(List<SoyValue> args) {
    return StringData.forValue(BidiFunctionsRuntime.bidiMark(bidiGlobalDirProvider.get()));
  }
  // lazy singleton pattern, allows other backends to avoid the work.
  private static final class JbcSrcMethods {

    static final MethodRef BIDI_MARK =
        MethodRef.create(BidiFunctionsRuntime.class, "bidiMark", BidiGlobalDir.class)
            .asNonNullable();
  }

  @Override
  public SoyExpression computeForJbcSrc(JbcSrcPluginContext context, List<SoyExpression> args) {
    return SoyExpression.forString(JbcSrcMethods.BIDI_MARK.invoke(context.getBidiGlobalDir()));
  }

  @Override
  public JsExpr computeForJsSrc(List<JsExpr> args) {
    BidiGlobalDir bidiGlobalDir = bidiGlobalDirProvider.get();
    if (bidiGlobalDir.isStaticValue()) {
      return new JsExpr(
          (bidiGlobalDir.getStaticValue() < 0) ? "'\\u200F'" /*RLM*/ : "'\\u200E'" /*LRM*/,
          Integer.MAX_VALUE);
    }
    return new JsExpr(
        "(" + bidiGlobalDir.getCodeSnippet() + ") < 0 ? '\\u200F' : '\\u200E'",
        Operator.CONDITIONAL.getPrecedence());
  }

  @Override
  public ImmutableSet<String> getRequiredJsLibNames() {
    return ImmutableSet.copyOf(bidiGlobalDirProvider.get().getNamespace().asSet());
  }

  @Override
  public PyExpr computeForPySrc(List<PyExpr> args) {
    BidiGlobalDir bidiGlobalDir = bidiGlobalDirProvider.get();
    return new PyExpr(
        "'\\u200F' if (" + bidiGlobalDir.getCodeSnippet() + ") < 0 else '\\u200E'",
        Operator.CONDITIONAL.getPrecedence());
  }
}
