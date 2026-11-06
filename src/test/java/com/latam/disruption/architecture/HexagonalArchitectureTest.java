package com.latam.disruption.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.latam.disruption.domain.exception.DomainOrTechnicalBaseException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

class HexagonalArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.latam.disruption");

    @Test
    void arquitecturaHexagonalRespetaDireccionDeDependencias() {
        layeredArchitecture()
                .consideringAllDependencies()
                .layer("Infrastructure").definedBy("..infrastructure..")
                .layer("Application").definedBy("..application..")
                .layer("Domain").definedBy("..domain..")
                .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
                .whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure")
                .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure")
                .check(classes);
    }

    @Test
    void dominioEsCienPorCientoPuroSinSpring() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAPackage("org.springframework..")
                .because("El dominio no puede acoplarse a Spring")
                .check(classes);
    }

    @Test
    void dominioEsCienPorCientoPuroSinJpaNiJackson() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("jakarta.persistence..", "com.fasterxml.jackson..")
                .because("El dominio no puede acoplarse a JPA ni Jackson")
                .check(classes);
    }

    @Test
    void puertosVivenEnApplicationYNoEnDomain() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().beInterfaces()
                .andShould().haveSimpleNameEndingWith("Port")
                .because("Los puertos son el borde del hexagono y viven en application.port")
                .check(classes);
    }

    @Test
    void adaptadoresDeSalidaImplementanPuertosDeSalida() {
        classes()
                .that().resideInAPackage("..infrastructure.adapter.out..")
                .and().haveSimpleNameEndingWith("Adapter")
                .should().dependOnClassesThat()
                .resideInAPackage("..application.port.out..")
                .because("Todo adaptador de salida implementa un puerto de salida")
                .check(classes);
    }

    @Test
    void todasLasExcepcionesDelDominioHeredanDeLaBaseControlada() {
        classes()
                .that().resideInAPackage("..domain.exception..")
                .and().areAssignableTo(RuntimeException.class)
                .should().beAssignableTo(DomainOrTechnicalBaseException.class)
                .because("Ninguna excepcion puede escapar de la jerarquia controlada hacia un 500 generico")
                .check(classes);
    }

    @Test
    void prohibidoFieldInjectionConAutowired() {
        noFields()
                .that().areDeclaredInClassesThat()
                .resideInAnyPackage("..application..", "..infrastructure..")
                .should().beAnnotatedWith(Autowired.class)
                .because("La inyeccion de dependencias debe hacerse por constructor explicito")
                .check(classes);
    }
}
