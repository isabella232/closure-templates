/*
 * Copyright 2018 Google Inc.
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
package com.google.template.soy.soytree;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.base.internal.TemplateContentKind;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.types.TemplateType;
import com.google.template.soy.types.TemplateType.DataAllCallSituation;
import com.google.template.soy.types.TemplateType.Parameter;
import com.google.template.soy.types.UnknownType;
import javax.annotation.Nullable;

/**
 * An abstract representation of a template that provides the minimal amount of information needed
 * compiling against dependency templates.
 *
 * <p>When compiling with dependencies the compiler needs to examine certain information from
 * dependent templates in order to validate calls and escape call sites. Traditionally, the Soy
 * compiler accomplished this by having each compilation parse all transitive dependencies. This is
 * an expensive solution. So instead of that we instead use this object to represent the minimal
 * information we need about dependencies.
 *
 * <p>The APIs on this class mirror ones available on {@link TemplateNode}.
 */
@AutoValue
public abstract class TemplateMetadata {

  /** Builds a Template from a parsed TemplateNode. */
  public static TemplateMetadata fromTemplate(TemplateNode template) {
    TemplateMetadata.Builder builder =
        builder()
            .setTemplateName(template.getTemplateName())
            .setSourceLocation(template.getSourceLocation())
            .setSoyFileKind(SoyFileKind.SRC)
            .setSoyElement(
                SoyElementMetadataP.newBuilder()
                    .setIsSoyElement(template instanceof TemplateElementNode)
                    .build())
            .setContentKind(template.getContentKind())
            .setStrictHtml(template.isStrictHtml())
            .setDelPackageName(template.getDelPackageName())
            .setVisibility(template.getVisibility())
            .setParameters(directParametersFromTemplate(template))
            .setDataAllCallSituations(dataAllCallSituationFromTemplate(template));
    // In various conditions such as Conformance tests, this can be null.
    if (template.getHtmlElementMetadata() != null) {
      builder.setHtmlElement(template.getHtmlElementMetadata());
    }
    switch (template.getKind()) {
      case TEMPLATE_BASIC_NODE:
        builder.setTemplateKind(TemplateType.TemplateKind.BASIC);
        break;
      case TEMPLATE_DELEGATE_NODE:
        builder.setTemplateKind(TemplateType.TemplateKind.DELTEMPLATE);
        TemplateDelegateNode deltemplate = (TemplateDelegateNode) template;
        builder.setDelTemplateName(deltemplate.getDelTemplateName());
        builder.setDelTemplateVariant(deltemplate.getDelTemplateVariant());
        break;
      case TEMPLATE_ELEMENT_NODE:
        builder.setTemplateKind(TemplateType.TemplateKind.ELEMENT);
        break;
      default:
        throw new AssertionError("unexpected template kind: " + template.getKind());
    }
    return builder.build();
  }

  public static TemplateMetadata.Builder builder() {
    return new AutoValue_TemplateMetadata.Builder();
  }

  private static ImmutableList<Parameter> directParametersFromTemplate(TemplateNode node) {
    ImmutableList.Builder<Parameter> params = ImmutableList.builder();
    for (TemplateParam param : node.getParams()) {
      params.add(parameterFromTemplateParam(param));
    }
    return params.build();
  }

  public static Parameter parameterFromTemplateParam(TemplateParam param) {
    return Parameter.builder()
        .setName(param.name())
        // Proto imports when compiler is not given proto descriptors will cause type to be unset.
        .setType(param.hasType() ? param.type() : UnknownType.getInstance())
        .setRequired(param.isRequired())
        .setDescription(param.desc())
        .build();
  }

  private static ImmutableList<DataAllCallSituation> dataAllCallSituationFromTemplate(
      TemplateNode node) {
    ImmutableSet.Builder<DataAllCallSituation> calls = ImmutableSet.builder();
    for (CallNode call : SoyTreeUtils.getAllNodesOfType(node, CallNode.class)) {
      if (call.isPassingAllData()) {
        DataAllCallSituation.Builder builder = DataAllCallSituation.builder();
        ImmutableSet.Builder<String> explicitlyPassedParams = ImmutableSet.builder();
        for (CallParamNode param : call.getChildren()) {
          explicitlyPassedParams.add(param.getKey().identifier());
        }
        builder.setExplicitlyPassedParameters(explicitlyPassedParams.build());
        switch (call.getKind()) {
          case CALL_BASIC_NODE:
            builder.setDelCall(false).setTemplateName(((CallBasicNode) call).getCalleeName());
            break;
          case CALL_DELEGATE_NODE:
            builder.setDelCall(true).setTemplateName(((CallDelegateNode) call).getDelCalleeName());
            break;
          default:
            throw new AssertionError("unexpected call kind: " + call.getKind());
        }
        calls.add(builder.build());
      }
    }
    return calls.build().asList();
  }

  public abstract SoyFileKind getSoyFileKind();

  /**
   * The source location of the template. For non {@code SOURCE} templates this will merely refer to
   * the file path, line and column information isn't recorded.
   */
  public abstract SourceLocation getSourceLocation();

  @Nullable
  public abstract HtmlElementMetadataP getHtmlElement();

  @Nullable
  public abstract SoyElementMetadataP getSoyElement();

  public abstract TemplateType.TemplateKind getTemplateKind();

  public abstract String getTemplateName();

  /** Guaranteed to be non-null for deltemplates, null otherwise. */
  @Nullable
  public abstract String getDelTemplateName();

  @Nullable
  public abstract String getDelTemplateVariant();

  public abstract SanitizedContentKind getContentKind();

  public abstract boolean isStrictHtml();

  public abstract Visibility getVisibility();

  @Nullable
  public abstract String getDelPackageName();

  /** The Parameters defined directly on the template. Includes {@code $ij} parameters. */
  public abstract ImmutableList<Parameter> getParameters();

  /**
   * The unique template calls that are performed by this template.
   *
   * <p>This is needed to calculate information about transitive parameters.
   */
  public abstract ImmutableList<DataAllCallSituation> getDataAllCallSituations();

  public abstract Builder toBuilder();

  /** Builder for {@link TemplateMetadata} */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setSoyFileKind(SoyFileKind location);

    public abstract Builder setSourceLocation(SourceLocation location);

    public abstract Builder setHtmlElement(HtmlElementMetadataP isHtml);

    public abstract Builder setSoyElement(SoyElementMetadataP isSoyEl);

    public abstract Builder setTemplateKind(TemplateType.TemplateKind kind);

    public abstract Builder setTemplateName(String templateName);

    public abstract Builder setDelTemplateName(String delTemplateName);

    public abstract Builder setDelTemplateVariant(String delTemplateVariant);

    public abstract Builder setContentKind(SanitizedContentKind contentKind);

    public abstract Builder setStrictHtml(boolean strictHtml);

    public abstract Builder setDelPackageName(@Nullable String delPackageName);

    public abstract Builder setVisibility(Visibility visibility);

    public abstract Builder setParameters(ImmutableList<Parameter> parameters);

    public abstract Builder setDataAllCallSituations(
        ImmutableList<DataAllCallSituation> dataAllCallSituations);

    public final TemplateMetadata build() {
      TemplateMetadata built = autobuild();
      if (built.getTemplateKind() == TemplateType.TemplateKind.DELTEMPLATE) {
        checkState(built.getDelTemplateName() != null, "Deltemplates must have a deltemplateName");
        checkState(
            built.getDelTemplateVariant() != null, "Deltemplates must have a deltemplateName");
      } else {
        checkState(
            built.getDelTemplateVariant() == null, "non-Deltemplates must not have a variant");
        checkState(
            built.getDelTemplateName() == null, "non-Deltemplates must not have a deltemplateName");
      }
      return built;
    }

    abstract TemplateMetadata autobuild();
  }

  public static TemplateType asTemplateType(TemplateMetadata templateMetadata) {
    return TemplateType.builder()
        .setTemplateKind(templateMetadata.getTemplateKind())
        .setContentKind(
            TemplateContentKind.fromSanitizedContentKind(templateMetadata.getContentKind()))
        .setStrictHtml(templateMetadata.isStrictHtml())
        .setParameters(
            templateMetadata.getParameters().stream()
                .map(
                    (param) ->
                        TemplateType.Parameter.builder()
                            .setName(param.getName())
                            .setType(param.getType())
                            .setRequired(param.isRequired())
                            .build())
                .collect(toImmutableList()))
        .setDataAllCallSituations(
            templateMetadata.getDataAllCallSituations().stream()
                .map(
                    (dataAllCallSituation) ->
                        DataAllCallSituation.builder()
                            .setTemplateName(dataAllCallSituation.getTemplateName())
                            .setDelCall(dataAllCallSituation.isDelCall())
                            .setExplicitlyPassedParameters(
                                dataAllCallSituation.getExplicitlyPassedParameters())
                            .build())
                .collect(toImmutableList()))
        .setIdentifierForDebugging(templateMetadata.getTemplateName())
        .setInferredType(true)
        .build();
  }
}
