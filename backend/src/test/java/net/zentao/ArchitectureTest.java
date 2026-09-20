package net.zentao;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/** 01 §2.3 分层铁律 A1–A5 首版；规则随包增加收紧（如 A4 在域出现后收窄为 ..infra.web..）。 */
class ArchitectureTest {

  private static final JavaClasses CLASSES =
      new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests()).importPackages("net.zentao");

  @Test
  @DisplayName("A1 · domain 包禁依赖 Spring/MyBatis-Flex/Servlet")
  void domainIsPure() {
    noClasses()
        .that()
        .resideInAPackage("..domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("org.springframework..", "com.mybatisflex..", "jakarta.servlet..")
        .allowEmptyShould(true)
        .check(CLASSES);
  }

  @Test
  @DisplayName("A2 · 实现层无循环依赖（api↔api 双向引用是设计允许的）")
  void contextsAreCycleFree() {
    // 切片只含实现层：product 需 requirement.api（发布结需求）、requirement 需 product.api（产品可见性），
    // 这类 api↔api 双向引用为 01 §2.3 所允许；app/domain/infra 之间成环才是违规。
    SlicesRuleDefinition.slices()
        .matching("net.zentao.(*).(app|domain|infra)..")
        .should()
        .beFreeOfCycles()
        .check(CLASSES);
  }

  /** 业务域全集（platform 不在此列：它不允许知道任何域，由 A3 反向约束）。 */
  private static final List<String> DOMAINS =
      List.of("org", "product", "requirement", "project", "task", "quality", "doc", "workspace");

  @Test
  @DisplayName("A2 · 跨域只经对方 api 包（app/domain/infra 不得被其他域依赖）")
  void crossDomainOnlyViaApiPackage() {
    for (String domain : DOMAINS) {
      for (String other : DOMAINS) {
        if (domain.equals(other)) {
          continue;
        }
        noClasses()
            .that()
            .resideInAPackage("net.zentao." + domain + "..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "net.zentao." + other + ".app..",
                "net.zentao." + other + ".domain..",
                "net.zentao." + other + ".infra..")
            .allowEmptyShould(true)
            .check(CLASSES);
      }
    }
  }

  @Test
  @DisplayName("A3 · platform 不知道任何业务域")
  void platformIsDomainAgnostic() {
    noClasses()
        .that()
        .resideInAPackage("..platform..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "net.zentao.org..",
            "net.zentao.product..",
            "net.zentao.requirement..",
            "net.zentao.project..",
            "net.zentao.task..",
            "net.zentao.quality..",
            "net.zentao.doc..",
            "net.zentao.workspace..")
        .check(CLASSES);
  }

  @Test
  @DisplayName("A4 · @RestController 只在 platform.web 或域 infra.web")
  void controllersAreInfraOnly() {
    classes()
        .that()
        .areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
        .should()
        .resideInAnyPackage("..platform.web..", "..infra.web..")
        .check(CLASSES);
  }

  @Test
  @DisplayName("A5 · @Transactional 只在 app 层")
  void transactionsLiveInAppLayer() {
    noClasses()
        .that()
        .resideOutsideOfPackage("..app..")
        .should()
        .beAnnotatedWith(Transactional.class)
        .check(CLASSES);
  }
}
