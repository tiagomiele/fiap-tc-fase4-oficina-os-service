package br.com.oficina.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.Architectures;
import org.junit.jupiter.api.Test;

/**
 * Valida a Clean Architecture (4 anéis) do monolito:
 *
 * <pre>
 *   infrastructure (config, composition root)
 *       → adapter (controllers, persistence, security, notification)
 *       → usecase (gateways + services)
 *       → domain (entities, value objects, enums)
 *   domain é puro (sem Spring/JPA/Servlet)
 * </pre>
 */
class ArchitectureTest {

  private static final JavaClasses CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("br.com.oficina");

  @Test
  void cleanArchitecture4CamadasRespeitaDependencias() {
    Architectures.layeredArchitecture()
        .consideringAllDependencies()
        .layer("infrastructure")
        .definedBy("br.com.oficina.infrastructure..")
        .layer("adapter")
        .definedBy("br.com.oficina.adapter..")
        .layer("usecase")
        .definedBy("br.com.oficina.usecase..")
        .layer("domain")
        .definedBy("br.com.oficina.domain..")
        .whereLayer("infrastructure")
        .mayNotBeAccessedByAnyLayer()
        .whereLayer("adapter")
        .mayOnlyBeAccessedByLayers("infrastructure")
        .whereLayer("usecase")
        .mayOnlyBeAccessedByLayers("adapter", "infrastructure")
        .whereLayer("domain")
        .mayOnlyBeAccessedByLayers("usecase", "adapter", "infrastructure")
        .check(CLASSES);
  }

  @Test
  void dominioNaoDependeDeSpring() {
    noClasses()
        .that()
        .resideInAPackage("br.com.oficina.domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework..",
            "jakarta.persistence..",
            "org.hibernate..",
            "jakarta.servlet..")
        .check(CLASSES);
  }

  @Test
  void dominioNaoUsaJpa() {
    noClasses()
        .that()
        .resideInAPackage("br.com.oficina.domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("jakarta.persistence..", "org.hibernate..")
        .check(CLASSES);
  }

  @Test
  void usecaseNaoDependeDeAdapterNemInfrastructure() {
    noClasses()
        .that()
        .resideInAPackage("br.com.oficina.usecase..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("br.com.oficina.adapter..", "br.com.oficina.infrastructure..")
        .check(CLASSES);
  }

  @Test
  void adapterNaoDependeDeInfrastructure() {
    noClasses()
        .that()
        .resideInAPackage("br.com.oficina.adapter..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("br.com.oficina.infrastructure..")
        .check(CLASSES);
  }
}
