package com.github.alexrichards.autogson;

import com.google.auto.value.AutoValue;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Generated;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;

public class AutoGsonProcessor extends AbstractProcessor {

    private Elements elementUtils;
    private Filer filer;
    private Messager messager;

    @Override
    public void init(final ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        elementUtils = processingEnv.getElementUtils();
        filer = processingEnv.getFiler();
        messager = processingEnv.getMessager();
    }

    @Override
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        for (final TypeElement annotation : annotations) {
            if (AutoValue.class.getCanonicalName().equals(annotation.getQualifiedName().toString())) {
                final Map<PackageElement, Set<TypeElement>> autoValueTypeElements = new HashMap<>();

                for (final Element autoValueElement : roundEnv.getElementsAnnotatedWith(annotation)) {
                    if (autoValueElement.getKind() != ElementKind.CLASS) {
                        messager.printMessage(Diagnostic.Kind.ERROR, String.format("%s cannot be applied to %s",
                                annotation.getSimpleName(), autoValueElement.getSimpleName()), autoValueElement);
                        continue;
                    }

                    final PackageElement packageElement = elementUtils.getPackageOf(autoValueElement);

                    Set<TypeElement> typeElements = autoValueTypeElements.get(packageElement);
                    if (typeElements == null) {
                        autoValueTypeElements.put(packageElement, typeElements = new LinkedHashSet<>());
                    }

                    typeElements.add((TypeElement) autoValueElement);
                }

                for (final Map.Entry<PackageElement, Set<TypeElement>> entry : autoValueTypeElements.entrySet()) {
                    try {
                        final TypeName classWildcardTypeName = TypeName.get(Class.class); // TODO ParameterizedTypeName.get(ClassName.get(Class.class), WildcardTypeName.subtypeOf(TypeName.OBJECT));
                        final TypeName mapTypeName = ParameterizedTypeName.get(ClassName.get(HashMap.class), classWildcardTypeName, classWildcardTypeName);

                        final FieldSpec mapField = FieldSpec.builder(mapTypeName, "TYPES", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                                .initializer("new $T()", mapTypeName)
                                .build();

                        final CodeBlock.Builder mappingInit = CodeBlock.builder();
                        for (final TypeElement typeElement : entry.getValue()) {
                            mappingInit.addStatement("$N.put($L.class, $L.class)", mapField, getName(typeElement), getAutoValueName(typeElement));
                        }

                        final TypeVariableName t = TypeVariableName.get("T");
                        final ParameterSpec gsonParameter = ParameterSpec.builder(Gson.class, "gson", Modifier.FINAL).build();
                        final ParameterSpec typeParameter = ParameterSpec.builder(ParameterizedTypeName.get(ClassName.get(TypeToken.class), t), "type", Modifier.FINAL).build();

                        final FieldSpec mappedTypeField = FieldSpec.builder(classWildcardTypeName, "mappedType", Modifier.FINAL).build();

                        JavaFile.builder(entry.getKey().getQualifiedName().toString(),
                                TypeSpec.classBuilder("AutoGsonTypeAdapterFactory")
                                        .addAnnotation(AnnotationSpec.builder(Generated.class)
                                                .addMember("value", "$S", AutoGsonProcessor.class.getCanonicalName())
                                                .build())
                                        .addModifiers(Modifier.PUBLIC)
                                        .addSuperinterface(TypeAdapterFactory.class)
                                        .addField(mapField)
                                        .addStaticBlock(mappingInit.build())
                                        .addMethod(MethodSpec.methodBuilder("create")
                                                .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class)
                                                        .addMember("value", "$S", "unchecked")
                                                        .build())
                                                .addTypeVariable(t)
                                                .addModifiers(Modifier.PUBLIC)
                                                .returns(ParameterizedTypeName.get(ClassName.get(TypeAdapter.class), t))
                                                .addParameter(gsonParameter)
                                                .addParameter(typeParameter)
                                                .addCode(CodeBlock.builder()
                                                        .addStatement("final $T $N = $N.get($N.getRawType())", mappedTypeField.type, mappedTypeField, mapField, typeParameter)
                                                        .beginControlFlow("if ($N != null)", mappedTypeField)
                                                        .addStatement("return $N.getAdapter($N)", gsonParameter, mappedTypeField)
                                                        .nextControlFlow("else")
                                                        .addStatement("return null")
                                                        .endControlFlow()
                                                        .build())
                                                .build())
                                        .build())
                                .build()
                                .writeTo(filer);
                    } catch (IOException e) {
                        messager.printMessage(Diagnostic.Kind.ERROR, e.getMessage());
                    }
                }
            }
        }

        return false;
    }

    private String getName(final TypeElement typeElement) {
        final StringBuilder builder = new StringBuilder();
        for (Element e = typeElement; e.getKind() == ElementKind.CLASS; e = e.getEnclosingElement()) {
            if (builder.length() > 0) {
                builder.insert(0, '.');
            }
            builder.insert(0, e.getSimpleName());
        }
        return builder.toString();
    }

    private String getAutoValueName(final TypeElement typeElement) {
        final StringBuilder builder = new StringBuilder();
        for (Element e = typeElement; e.getKind() == ElementKind.CLASS; e = e.getEnclosingElement()) {
            if (builder.length() > 0) {
                builder.insert(0, '_');
            }
            builder.insert(0, e.getSimpleName());
        }
        builder.insert(0, "AutoValue_");
        return builder.toString();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton(AutoValue.class.getCanonicalName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }
}
