/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.jssrc.internal;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.basicfunctions.BasicFunctionsModule;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.IntegerNode;
import com.google.template.soy.exprtree.NullNode;
import com.google.template.soy.exprtree.OperatorNodes.TimesOpNode;
import com.google.template.soy.jssrc.restricted.JsExpr;

import junit.framework.TestCase;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

/**
 * Unit tests for JsExprTranslator.
 *
 */
public class JsExprTranslatorTest extends TestCase {


  private static final Injector INJECTOR =
      Guice.createInjector(new JsSrcModule(), new BasicFunctionsModule());


  public void testTranslateToJsExpr() {

    JsSrcTestUtils.simulateNewApiCall(INJECTOR);
    JsExprTranslator jsExprTranslator = INJECTOR.getInstance(JsExprTranslator.class);

    TimesOpNode expr = new TimesOpNode();
    expr.addChild(new IntegerNode(3));
    expr.addChild(new NullNode());  // will be replaced with one of the functions below

    FunctionNode userFnNode = new FunctionNode("userFn");
    userFnNode.addChild(new IntegerNode(5));

    FunctionNode randomIntFnNode = new FunctionNode("randomInt");
    randomIntFnNode.addChild(new IntegerNode(4));

    Deque<Map<String, JsExpr>> localVarTranslations = new ArrayDeque<Map<String, JsExpr>>();

    // Test unsupported function (Soy V1 syntax).
    expr.replaceChild(1, userFnNode);
    String exprText = "3   *   userFn(5)";
    assertEquals(
        "3 * userFn(5)",
        jsExprTranslator.translateToJsExpr(expr, exprText, localVarTranslations).getText());

    // Test supported function.
    expr.replaceChild(1, randomIntFnNode);
    exprText = "3   *   randomInt(4)";
    assertEquals(
        "3 * Math.floor(Math.random() * 4)",
        jsExprTranslator.translateToJsExpr(expr, exprText, localVarTranslations).getText());

    // Test supported function with wrong number of args.
    randomIntFnNode.removeChild(0);
    exprText = "3   *   randomInt()";
    try {
      jsExprTranslator.translateToJsExpr(expr, exprText, localVarTranslations);
      fail();
    } catch (SoySyntaxException sse) {
      // Test passes.
    }
  }

}
