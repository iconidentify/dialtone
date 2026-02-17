/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.fdo.dsl;

import com.dialtone.fdo.dsl.FdoDslBuilder;
import com.dialtone.fdo.dsl.FdoDslRegistry;
import com.dialtone.fdo.dsl.RenderingContext;
import com.dialtone.fdo.dsl.builders.NoopFdoBuilder;
import org.junit.jupiter.api.*;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FdoDslRegistry")
class FdoDslRegistryTest {

    private FdoDslRegistry registry;

    /**
     * Simple test builder implementation for testing purposes.
     */
    private static class TestBuilder implements FdoDslBuilder {
        private final String gid;
        private final String description;
        private final boolean hasBwVariant;

        TestBuilder(String gid, String description) {
            this(gid, description, false);
        }

        TestBuilder(String gid, String description, boolean hasBwVariant) {
            this.gid = gid;
            this.description = description;
            this.hasBwVariant = hasBwVariant;
        }

        @Override
        public String getGid() {
            return gid;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public String toSource(RenderingContext context) {
            if (context.isLowColorMode()) {
                return "uni_start_stream <00x>\n// BW variant\nuni_end_stream";
            }
            return "uni_start_stream <00x>\nuni_end_stream";
        }

        @Override
        @SuppressWarnings("deprecation")
        public String toSource(Map<String, Object> variables) {
            return toSource(RenderingContext.DEFAULT);
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean hasBwVariant() {
            return hasBwVariant;
        }

        @Override
        @SuppressWarnings("deprecation")
        public String toSourceBw(Map<String, Object> variables) {
            return "uni_start_stream <00x>\n// BW variant\nuni_end_stream";
        }
    }

    @BeforeEach
    void setUp() {
        FdoDslRegistry.resetInstance();
        registry = FdoDslRegistry.getInstance();
    }

    @AfterEach
    void tearDown() {
        registry.clear();
    }

    @Nested
    @DisplayName("Singleton behavior")
    class SingletonBehavior {

        @Test
        @DisplayName("Should return same instance on multiple calls")
        void shouldBeSingleton() {
            FdoDslRegistry instance1 = FdoDslRegistry.getInstance();
            FdoDslRegistry instance2 = FdoDslRegistry.getInstance();
            assertSame(instance1, instance2);
        }

        @Test
        @DisplayName("Should create new instance after reset")
        void shouldCreateNewInstanceAfterReset() {
            FdoDslRegistry instance1 = FdoDslRegistry.getInstance();
            FdoDslRegistry.resetInstance();
            FdoDslRegistry instance2 = FdoDslRegistry.getInstance();
            assertNotSame(instance1, instance2);
        }
    }

    @Nested
    @DisplayName("Builder registration")
    class BuilderRegistration {

        @Test
        @DisplayName("Should register builder successfully")
        void shouldRegisterBuilder() {
            TestBuilder builder = new TestBuilder("69-420", "Test builder");
            registry.registerBuilder(builder);

            assertTrue(registry.hasBuilder("69-420"));
            assertEquals(1, registry.getBuilderCount());
        }

        @Test
        @DisplayName("Should reject null builder")
        void shouldRejectNullBuilder() {
            assertThrows(IllegalArgumentException.class, () ->
                registry.registerBuilder(null));
        }

        @Test
        @DisplayName("Should reject builder with null GID")
        void shouldRejectBuilderWithNullGid() {
            TestBuilder builder = new TestBuilder(null, "Test");
            assertThrows(IllegalArgumentException.class, () ->
                registry.registerBuilder(builder));
        }

        @Test
        @DisplayName("Should reject builder with empty GID")
        void shouldRejectBuilderWithEmptyGid() {
            TestBuilder builder = new TestBuilder("", "Test");
            assertThrows(IllegalArgumentException.class, () ->
                registry.registerBuilder(builder));
        }

        @Test
        @DisplayName("Should reject builder with whitespace-only GID")
        void shouldRejectBuilderWithWhitespaceGid() {
            TestBuilder builder = new TestBuilder("   ", "Test");
            assertThrows(IllegalArgumentException.class, () ->
                registry.registerBuilder(builder));
        }

        @Test
        @DisplayName("Should replace existing builder for same GID")
        void shouldReplaceExistingBuilder() {
            TestBuilder builder1 = new TestBuilder("69-420", "First");
            TestBuilder builder2 = new TestBuilder("69-420", "Second");

            registry.registerBuilder(builder1);
            registry.registerBuilder(builder2);

            assertEquals(1, registry.getBuilderCount());
            assertEquals("Second", registry.getBuilder("69-420").orElseThrow().getDescription());
        }
    }

    @Nested
    @DisplayName("Builder lookup")
    class BuilderLookup {

        @Test
        @DisplayName("Should retrieve builder by exact GID")
        void shouldRetrieveByExactGid() {
            TestBuilder builder = new TestBuilder("69-420", "Test");
            registry.registerBuilder(builder);

            Optional<FdoDslBuilder> result = registry.getBuilder("69-420");
            assertTrue(result.isPresent());
            assertEquals("69-420", result.get().getGid());
        }

        @Test
        @DisplayName("Should retrieve builder case-insensitively")
        void shouldRetrieveCaseInsensitively() {
            TestBuilder builder = new TestBuilder("69-420", "Test");
            registry.registerBuilder(builder);

            assertTrue(registry.getBuilder("69-420").isPresent());
            assertTrue(registry.getBuilder("69-420").isPresent());
        }

        @Test
        @DisplayName("Should trim whitespace in lookup")
        void shouldTrimWhitespaceInLookup() {
            TestBuilder builder = new TestBuilder("69-420", "Test");
            registry.registerBuilder(builder);

            assertTrue(registry.getBuilder("  69-420  ").isPresent());
            assertTrue(registry.getBuilder("\t69-420\n").isPresent());
        }

        @Test
        @DisplayName("Should return empty Optional for unknown GID")
        void shouldReturnEmptyForUnknownGid() {
            assertTrue(registry.getBuilder("99-999").isEmpty());
        }

        @Test
        @DisplayName("Should return empty Optional for null GID")
        void shouldReturnEmptyForNullGid() {
            assertTrue(registry.getBuilder(null).isEmpty());
        }

        @Test
        @DisplayName("Should return empty Optional for empty GID")
        void shouldReturnEmptyForEmptyGid() {
            assertTrue(registry.getBuilder("").isEmpty());
        }

        @Test
        @DisplayName("hasBuilder should return true for registered GID")
        void hasBuilderShouldReturnTrueForRegisteredGid() {
            registry.registerBuilder(new TestBuilder("69-420", "Test"));
            assertTrue(registry.hasBuilder("69-420"));
        }

        @Test
        @DisplayName("hasBuilder should return false for unregistered GID")
        void hasBuilderShouldReturnFalseForUnregisteredGid() {
            assertFalse(registry.hasBuilder("69-420"));
        }
    }

    @Nested
    @DisplayName("Builder unregistration")
    class BuilderUnregistration {

        @Test
        @DisplayName("Should unregister builder")
        void shouldUnregisterBuilder() {
            registry.registerBuilder(new TestBuilder("69-420", "Test"));
            assertTrue(registry.hasBuilder("69-420"));

            boolean removed = registry.unregisterBuilder("69-420");

            assertTrue(removed);
            assertFalse(registry.hasBuilder("69-420"));
        }

        @Test
        @DisplayName("Should return false when unregistering unknown GID")
        void shouldReturnFalseForUnknownGid() {
            assertFalse(registry.unregisterBuilder("99-999"));
        }

        @Test
        @DisplayName("Should return false when unregistering null GID")
        void shouldReturnFalseForNullGid() {
            assertFalse(registry.unregisterBuilder(null));
        }

        @Test
        @DisplayName("Should return false when unregistering empty GID")
        void shouldReturnFalseForEmptyGid() {
            assertFalse(registry.unregisterBuilder(""));
        }
    }

    @Nested
    @DisplayName("Registry state")
    class RegistryState {

        @Test
        @DisplayName("Should start empty")
        void shouldStartEmpty() {
            assertTrue(registry.isEmpty());
            assertEquals(0, registry.getBuilderCount());
        }

        @Test
        @DisplayName("Should not be empty after registration")
        void shouldNotBeEmptyAfterRegistration() {
            registry.registerBuilder(new TestBuilder("69-420", "Test"));
            assertFalse(registry.isEmpty());
        }

        @Test
        @DisplayName("Should clear all builders")
        void shouldClearAllBuilders() {
            registry.registerBuilder(new TestBuilder("69-420", "First"));
            registry.registerBuilder(new TestBuilder("32-117", "Second"));
            assertEquals(2, registry.getBuilderCount());

            registry.clear();

            assertTrue(registry.isEmpty());
            assertEquals(0, registry.getBuilderCount());
        }

        @Test
        @DisplayName("getAllBuilders should return snapshot")
        void getAllBuildersShouldReturnSnapshot() {
            registry.registerBuilder(new TestBuilder("69-420", "Test"));
            var builders = registry.getAllBuilders();

            assertEquals(1, builders.size());
        }
    }

    @Nested
    @DisplayName("Builder interface defaults")
    class BuilderInterfaceDefaults {

        @Test
        @DisplayName("hasBwVariant should default to false")
        void hasBwVariantShouldDefaultToFalse() {
            TestBuilder builder = new TestBuilder("69-420", "Test");
            assertFalse(builder.hasBwVariant());
        }

        @Test
        @DisplayName("toSourceBw should default to toSource")
        @SuppressWarnings("deprecation")
        void toSourceBwShouldDefaultToSource() {
            FdoDslBuilder builder = new FdoDslBuilder() {
                @Override public String getGid() { return "test"; }
                @Override public String getDescription() { return "test"; }
                @Override public String toSource(RenderingContext context) {
                    return "standard source";
                }
            };

            assertEquals("standard source", builder.toSourceBw(null));
        }

        @Test
        @DisplayName("Should support BW variant when implemented")
        void shouldSupportBwVariantWhenImplemented() {
            TestBuilder builder = new TestBuilder("69-420", "Test", true);
            assertTrue(builder.hasBwVariant());
            assertTrue(builder.toSourceBw(null).contains("BW variant"));
        }
    }

    @Nested
    @DisplayName("NoOp builder registration")
    class NoOpBuilderRegistration {

        @Test
        @DisplayName("NoOp builder should be retrievable by GID")
        void noOpBuilderShouldBeRetrievableByGid() {
            registry.registerBuilder(NoopFdoBuilder.INSTANCE);
            
            Optional<FdoDslBuilder> builder = registry.getBuilder("noop");
            assertTrue(builder.isPresent());
            assertEquals(NoopFdoBuilder.INSTANCE, builder.get());
            assertEquals("noop", builder.get().getGid());
        }

        @Test
        @DisplayName("NoOp builder should generate valid FDO source")
        void noOpBuilderShouldGenerateValidFdoSource() {
            registry.registerBuilder(NoopFdoBuilder.INSTANCE);
            
            Optional<FdoDslBuilder> builder = registry.getBuilder("noop");
            assertTrue(builder.isPresent());
            
            String source = builder.get().toSource(RenderingContext.DEFAULT);
            assertNotNull(source);
            assertTrue(source.contains("uni_start_stream"));
            assertTrue(source.contains("uni_wait_off"));
            assertTrue(source.contains("uni_end_stream"));
        }
    }
}
