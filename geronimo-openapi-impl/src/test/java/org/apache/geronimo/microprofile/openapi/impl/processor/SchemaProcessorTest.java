/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.geronimo.microprofile.openapi.impl.processor;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toSet;
import static org.testng.Assert.assertEquals;

import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.json.bind.annotation.JsonbProperty;

import org.apache.geronimo.microprofile.openapi.impl.model.ComponentsImpl;
import org.apache.geronimo.microprofile.openapi.openjpa.Entity1;
import org.eclipse.microprofile.openapi.models.Components;
import org.eclipse.microprofile.openapi.models.media.Schema;
import org.testng.annotations.Test;

public class SchemaProcessorTest {
    @Test
    public void mapJsonp() {
        Stream.of(JsonValue.class, JsonObject.class).forEach(it -> {
            final Schema schema = new SchemaProcessor().mapSchemaFromClass(newComponentsProvider(), JsonValue.class);
            assertEquals(schema.getProperties().size(), 0);
            assertEquals(Schema.SchemaType.OBJECT, schema.getType());
        });
        final Schema schema = new SchemaProcessor().mapSchemaFromClass(newComponentsProvider(), JsonArray.class);
        assertEquals(Schema.SchemaType.ARRAY, schema.getType());
    }

    @Test
    public void mapImplicit() {
        Supplier<Components> components = newComponentsProvider();
        final Schema schema = getReferredSchema(components.get(), new SchemaProcessor().mapSchemaFromClass(components, Data.class));
        assertEquals(1, schema.getProperties().size());
        assertEquals(Schema.SchemaType.STRING, schema.getProperties().get("name").getType());
    }

    @Test
    public void mapJsonb() {
        Supplier<Components> components = newComponentsProvider();
        final Schema schema = getReferredSchema(components.get(), new SchemaProcessor().mapSchemaFromClass(components, JsonbData.class));
        assertEquals(1, schema.getProperties().size());
        assertEquals(Schema.SchemaType.STRING, schema.getProperties().get("foo").getType());
    }

    @Test
    public void mapEnum() {
        Supplier<Components> components = newComponentsProvider();
        final Schema schema = getReferredSchema(components.get(), new SchemaProcessor().mapSchemaFromClass(components, DataWithEnum.class));
        assertEquals(1, schema.getProperties().size());
        final Schema anEnum = schema.getProperties().get("anEnum");
        assertEquals(Schema.SchemaType.STRING, anEnum.getType());
        assertEquals(asList(AnEnum.A, AnEnum.B), anEnum.getEnumeration());
    }

    @Test
    public void cyclicRef() {
        final Components components = new ComponentsImpl();
        final Schema schema = getReferredSchema(components, new SchemaProcessor().mapSchemaFromClass(() -> components, SomeClass.class));
        assertEquals(3, schema.getProperties().size());
        assertEquals(Schema.SchemaType.STRING, schema.getProperties().get("simple").getType());
        assertSomeClass(schema.getProperties().get("child"));
        final Schema children = schema.getProperties().get("children");
        assertEquals(Schema.SchemaType.ARRAY, children.getType());
        assertSomeRelatedClass(children.getItems());
        assertEquals(2, components.getSchemas().size());
        final Schema completeSchema =
            components.getSchemas().get("org_apache_geronimo_microprofile_openapi_impl_processor_SchemaProcessorTest_SomeClass");
        assertEquals(3, completeSchema.getProperties().size());
        assertEquals(Stream.of("simple", "child", "children").collect(toSet()), completeSchema.getProperties().keySet());
    }

    @Test
    public void array() {
        final Components components = new ComponentsImpl();
        final Schema schema = getReferredSchema(components, new SchemaProcessor().mapSchemaFromClass(() -> components, SomeClassWithArray.class));
        assertEquals(1, schema.getProperties().size());
        final Schema array = schema.getProperties().get("thearray");
        assertEquals(Schema.SchemaType.ARRAY, array.getType());
        assertEquals(Schema.SchemaType.STRING, array.getItems().getType());
    }

    @Test
    public void clazz() {
        final Components components = new ComponentsImpl();
        final Schema schema = getReferredSchema(components, new SchemaProcessor().mapSchemaFromClass(() -> components, SomeClassField.class));
        assertEquals(schema.getProperties().size(), 1);
        final Schema field = schema.getProperties().get("clazz");
        assertEquals(Schema.SchemaType.STRING, field.getType());
    }

    @Test
    public void type() {
        final Components components = new ComponentsImpl();
        Schema refSchema = new SchemaProcessor().mapSchemaFromClass(() -> components, SomeTypeField.class);
        //verify that return schema has ref to the actual schema in the components
        assertEquals("#/components/schemas/org_apache_geronimo_microprofile_openapi_impl_processor_SchemaProcessorTest_SomeTypeField", refSchema.getRef());

        final Schema schema = getReferredSchema(components, refSchema);
        assertEquals(schema.getProperties().size(), 1);
        final Schema field = schema.getProperties().get("type");
        assertEquals(Schema.SchemaType.STRING, field.getType());
    }

    @Test
    public void openjpa() {
        final Components components = new ComponentsImpl();
        final Schema schema = getReferredSchema(components, new SchemaProcessor().mapSchemaFromClass(() -> components, Entity1.class));
        assertEquals(schema.getProperties().size(), 4);
        assertEquals(Schema.SchemaType.STRING, schema.getProperties().get("string").getType());
        assertEquals(Schema.SchemaType.INTEGER, schema.getProperties().get("id").getType());
        assertEquals(Schema.SchemaType.STRING, schema.getProperties().get("date").getType());
        assertEquals(Schema.SchemaType.ARRAY, schema.getProperties().get("relationship").getType());
    }

    @Test
    public void refSameSchema() {
        final Components components = new ComponentsImpl();
        Schema refSchema = new SchemaProcessor().mapSchemaFromClass(() -> components, SomeClassWithTwoIdenticalObjects.class);
        //verify that return schema has ref to the actual schema in the components
        assertEquals("#/components/schemas/org_apache_geronimo_microprofile_openapi_impl_processor_SchemaProcessorTest_SomeClassWithTwoIdenticalObjects", refSchema.getRef());

        final Schema schema = getReferredSchema(components, refSchema);
        assertEquals(schema.getProperties().size(), 2);
        final Schema type = schema.getProperties().get("type");
        final Schema anotherType = schema.getProperties().get("anotherType");
        //verify that both properties are referring to the same schema
        assertEquals("#/components/schemas/org_apache_geronimo_microprofile_openapi_impl_processor_SchemaProcessorTest_SomeTypeField", type.getRef());
        assertEquals("#/components/schemas/org_apache_geronimo_microprofile_openapi_impl_processor_SchemaProcessorTest_SomeTypeField", anotherType.getRef());
    }

    private Supplier<Components> newComponentsProvider() {
        final ComponentsImpl components = new ComponentsImpl();
        return () -> components;
    }

    private void assertSomeClass(final Schema schema) {
        assertEquals(Schema.SchemaType.OBJECT, schema.getType());
        assertEquals("#/components/schemas/org_apache_geronimo_microprofile_openapi_impl_processor_SchemaProcessorTest_SomeClass", schema.getRef());
    }

    private void assertSomeRelatedClass(final Schema schema) {
        assertEquals(Schema.SchemaType.OBJECT, schema.getType());
        assertEquals("#/components/schemas/org_apache_geronimo_microprofile_openapi_impl_processor_SchemaProcessorTest_SomeRelatedClass", schema.getRef());
    }

    /**
     * Get the referred schema, if available
     *
     * @param components all components
     * @param schema     schema
     * @return returns the referred schema or the passed one
     */
    private Schema getReferredSchema(Components components, Schema schema) {
        if (schema.getRef() != null) {
            String[] splits = schema.getRef().split("/");
            return components.getSchemas().get(splits[splits.length - 1]);
        }

        return schema;
    }

    public enum AnEnum {
        A, B
    }

    public static class SomeTypeField {
        protected Type type;
    }

    public static class SomeClassWithTwoIdenticalObjects{
        protected SomeTypeField type;
        protected SomeTypeField anotherType;
    }

    public static class SomeClassField {
        protected Class<?> clazz;
    }

    public static class SomeClass {
        protected String simple;
        protected List<SomeRelatedClass> children;
        protected SomeClass child;
    }

    public static class SomeRelatedClass {
        protected String simple;
        protected List<SomeRelatedClass> children;
    }

    public static class SomeClassWithArray {
        protected String[] thearray;
    }

    public static class DataWithEnum {
        protected AnEnum anEnum;
    }

    public static class Data {
        protected String name;
    }

    public static class JsonbData {
        @JsonbProperty("foo")
        protected String name;
    }
}
