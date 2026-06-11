package ch.example.app.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Erzwingt die Architektur-Invarianten des Blueprints (§3.4) als Build-Gate.
 * Architektur, die nicht getestet wird, erodiert.
 */
@AnalyzeClasses(packages = "ch.example.app", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule domain_ist_framework_frei = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "jakarta..", "io.quarkus..", "org.hibernate..",
                    "com.fasterxml.jackson..", "io.smallrye..", "org.jboss..")
            .because("das innere Hexagon hat null Framework-Abhängigkeiten (Blueprint §3.2)");

    @ArchTest
    static final ArchRule schichten_regel = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("domain").definedBy("..domain..")
            .layer("application").definedBy("..application..")
            .layer("adapter").definedBy("..adapter..")
            .whereLayer("adapter").mayNotBeAccessedByAnyLayer()
            .whereLayer("application").mayOnlyBeAccessedByLayers("adapter")
            .whereLayer("domain").mayOnlyBeAccessedByLayers("application", "adapter")
            .because("adapter -> application -> domain ist unverhandelbar (Blueprint §3.2)");

    @ArchTest
    static final ArchRule rest_dtos_bleiben_im_rest_adapter = classes()
            .that().resideInAPackage("..adapter.in.rest.dto..")
            .should().onlyBeAccessed().byClassesThat().resideInAPackage("..adapter.in.rest..")
            .because("REST-DTOs sind Transport-Objekte der REST-Schicht (Blueprint §3.4)");

    @ArchTest
    static final ArchRule jpa_entities_nur_im_persistence_adapter = noClasses()
            .that().resideOutsideOfPackage("..adapter.out.persistence..")
            .should().dependOnClassesThat().areAnnotatedWith(jakarta.persistence.Entity.class)
            .because("JPA-Entities leben ausschliesslich im Persistence-Adapter (Blueprint §4)");
}
