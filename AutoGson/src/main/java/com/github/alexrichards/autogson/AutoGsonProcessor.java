package com.github.alexrichards.autogson;

import com.google.auto.value.AutoValue;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.squareup.javapoet.*;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.*;

public class AutoGsonProcessor extends AbstractProcessor {

    private Types typeUtils;
    private Elements elementUtils;
    private Filer filer;
    private Messager messager;

    private Map<PackageElement, Set<TypeElement>> autoValueTypeElements = new HashMap<PackageElement, Set<TypeElement>>();

    @Override
    public void init(final ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        typeUtils = processingEnv.getTypeUtils();
        elementUtils = processingEnv.getElementUtils();
        filer = processingEnv.getFiler();
        messager = processingEnv.getMessager();
    }

    @Override
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        for (final TypeElement annotation : annotations) {
            if (AutoValue.class.getCanonicalName().equals(annotation.getQualifiedName().toString())) {
                for (final Element autoValueElement : roundEnv.getElementsAnnotatedWith(annotation)) {
                    if (autoValueElement.getKind() != ElementKind.CLASS) {
                        messager.printMessage(Diagnostic.Kind.ERROR, String.format("%s cannot be applied to %s",
                                annotation.getSimpleName(), autoValueElement.getSimpleName()), autoValueElement);
                        continue;
                    }

                    final PackageElement packageElement = elementUtils.getPackageOf(autoValueElement);

                    Set<TypeElement> typeElements = autoValueTypeElements.get(packageElement);
                    if (typeElements == null) {
                        autoValueTypeElements.put(packageElement, typeElements = new LinkedHashSet<TypeElement>());
                    }

                    typeElements.add((TypeElement) autoValueElement);
                }
            }
        }

        if (roundEnv.processingOver()) {
            for (final Map.Entry<PackageElement, Set<TypeElement>> entry : autoValueTypeElements.entrySet()) {
                try {
                    final String mapFieldName = "TYPES";

                    final TypeVariableName t = TypeVariableName.get("T");
                    final TypeName classWildcardTypeName = TypeName.get(Class.class); // TODO ParameterizedTypeName.get(ClassName.get(Class.class), WildcardTypeName.subtypeOf(TypeName.OBJECT));
                    final TypeName mapTypeName = ParameterizedTypeName.get(ClassName.get(HashMap.class), classWildcardTypeName, classWildcardTypeName);

                    final FieldSpec map = FieldSpec.builder(mapTypeName, mapFieldName, Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                            .initializer("new $T()", mapTypeName)
                            .build();

                    final CodeBlock.Builder mappingInit = CodeBlock.builder();
                    for (final TypeElement typeElement : entry.getValue()) {
                        mappingInit.addStatement("$N.put($L.class, $L.class)", map, getName(typeElement), getAutoValueName(typeElement));
                    }

                    final String gsonParameterName = "gson";
                    final String typeParameterName = "type";

                    JavaFile.builder(entry.getKey().getQualifiedName().toString(),
                            TypeSpec.classBuilder("AutoGsonTypeAdapterFactory")
                                    .addSuperinterface(TypeAdapterFactory.class)
                                    .addField(map)
                                    .addStaticBlock(mappingInit.build())
                                    .addMethod(MethodSpec.methodBuilder("create")
                                            .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class)
                                                    .addMember("value", "$S", "unchecked")
                                                    .build())
                                            .addTypeVariable(t)
                                            .addModifiers(Modifier.PUBLIC)
                                            .returns(ParameterizedTypeName.get(ClassName.get(TypeAdapter.class), t))
                                            .addParameter(Gson.class, gsonParameterName, Modifier.FINAL)
                                            .addParameter(ParameterizedTypeName.get(ClassName.get(TypeToken.class), t), typeParameterName, Modifier.FINAL)
                                            .addStatement("return $L.getAdapter($L.get($L.getRawType()))", gsonParameterName, mapFieldName, typeParameterName)
                                            .build())
                                    .build())
                            .build()
                            .writeTo(filer);
                } catch (IOException e) {
                    messager.printMessage(Diagnostic.Kind.ERROR, e.getMessage());
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
