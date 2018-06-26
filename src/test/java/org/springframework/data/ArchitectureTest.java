package org.springframework.data;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption.DontIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchUnitRunner;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.runner.RunWith;

import static com.tngtech.archunit.core.domain.Formatters.formatLocation;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@RunWith(ArchUnitRunner.class)
@AnalyzeClasses(packagesOf = ArchitectureTest.class, importOptions = DontIncludeTests.class)
public class ArchitectureTest {
    private static final Module web = Module.name("Web").identifiedBy("..web..");
    private static final Module repository = Module.name("Repositories").identifiedBy("..repository..", "..querydsl..");
    private static final Module auditing = Module.name("Auditing").identifiedBy("..auditing..");
    private static final Module conversion = Module.name("Conversion").identifiedBy("..convert..");
    private static final Module mapping = Module.name("Mapping").identifiedBy("..mapping..");

    private static final Module application = Module.name("Application")
            .identifiedBy("..domain..", "..crossstore..", "..geo..", "..history..", "..support..");
    private static final Module core = Module.name("Core")
            .identifiedBy("..util..", "..annotation..", "..authentication..", "..transaction..", "..projection..");

    @ArchTest
    public static final ArchRule web_satisfies_architecture =
            web.allowDependencyTo(repository)
                    .allowDependencyTo(mapping)
                    .allowDependencyTo(application)
                    .allowDependencyTo(core)
                    .allowDependencyToExternal()
                    .asRule();

    @ArchTest
    public static final ArchRule repository_satisfies_architecture =
            repository.allowDependencyTo(mapping)
                    .allowDependencyTo(application)
                    .allowDependencyTo(core)
                    .allowDependencyToExternal()
                    .asRule();

    @ArchTest
    public static final ArchRule auditing_satisfies_architecture =
            auditing.allowDependencyTo(conversion)
                    .allowDependencyTo(mapping)
                    .allowDependencyTo(application)
                    .allowDependencyTo(core)
                    .allowDependencyToExternal()
                    .asRule();

    @ArchTest
    public static final ArchRule conversion_satisfies_architecture =
            conversion.allowDependencyTo(mapping)
                    .allowDependencyTo(core)
                    .allowDependencyToExternal()
                    .asRule();

    @ArchTest
    public static final ArchRule mapping_satisfies_architecture =
            mapping.allowDependencyTo(application)
                    .allowDependencyTo(core)
                    .allowDependencyToExternal()
                    .asRule();

    @ArchTest
    public static final ArchRule application_satisfies_architecture =
            application.allowDependencyTo(core)
                    .allowDependencyToExternal()
                    .asRule();

    @ArchTest
    public static final ArchRule core_satisfies_architecture =
            core.allowDependencyToExternal().asRule();

    private static class Module {
        static final String ARE_ANY_OF = "are any of";

        private final String name;
        private final DescribedPredicate<JavaClass> belongToModule;
        private final DescribedPredicate<JavaClass> areAllowedDependencies;

        private Module(String name, DescribedPredicate<JavaClass> belongToModule) {
            this(name, belongToModule, belongToModule.as(ARE_ANY_OF));
        }

        private Module(String name, DescribedPredicate<JavaClass> belongToModule, DescribedPredicate<JavaClass> areAllowedDependencies) {
            this.name = name;
            this.belongToModule = belongToModule.as("are '%s'", name);
            this.areAllowedDependencies = areAllowedDependencies;
        }

        Module allowDependencyTo(Module other) {
            String descriptionTemplate = areAllowedDependencies.getDescription().equals(ARE_ANY_OF) ? "%s %s" : "%s, %s";
            String description = String.format(descriptionTemplate, areAllowedDependencies.getDescription(), other.name);
            return new Module(name, belongToModule, areAllowedDependencies.or(other.belongToModule).as(description));
        }

        ArchRule asRule() {
            return classes().that(belongToModule).should(onlyAccessClassesThat(areAllowedDependencies));
        }

        private ArchCondition<JavaClass> onlyAccessClassesThat(final DescribedPredicate<JavaClass> predicate) {
            return new ArchCondition<JavaClass>(
                    "only access classes that " + predicate.getDescription()) {
                @Override
                public void check(JavaClass javaClass, ConditionEvents conditionEvents) {
                    for (JavaAccess<?> access : javaClass.getAccessesFromSelf()) {
                        boolean satisfied = predicate.apply(access.getTargetOwner());
                        String message = String.format("class %s accesses %s in %s",
                                javaClass.getName(), access.getTargetOwner().getName(), formatLocation(javaClass, access.getLineNumber()));
                        conditionEvents.add(new SimpleConditionEvent(access, satisfied, message));
                    }
                }
            };
        }

        Module allowDependencyToExternal() {
            return new Module(name, belongToModule, areAllowedDependencies.or(areExternal));
        }

        // Note: Classes outside of the scope (where by default only a stub is created, e.g. java.util.List) have no source.
        // Compare https://www.archunit.org/userguide/html/000_Index.html#_dealing_with_missing_classes
        static final DescribedPredicate<JavaClass> areExternal = new DescribedPredicate<JavaClass>("are external") {
            @Override
            public boolean apply(JavaClass javaClass) {
                return !javaClass.getSource().isPresent() || javaClass.getSource().get().getUri().toString().startsWith("jar:");
            }
        };

        static Creator name(String name) {
            return new Creator(name);
        }

        private static class Creator {
            private final String name;

            Creator(String name) {
                this.name = name;
            }

            Module identifiedBy(String... packageIdentifiers) {
                return new Module(name, JavaClass.Predicates.resideInAnyPackage(packageIdentifiers));
            }
        }
    }
}
