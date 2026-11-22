<#import "/lib/java.ftl" as j>
package ${project.basePackage}.mapper;

import ${project.basePackage}.dto.*;
import ${project.basePackage}.support.Expressions;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

/**
 * Mappings for the transform rules, and for converting a payload into an event or a response.
 * Fields with the same name are copied; @Mapping lists the rest.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
        imports = {Expressions.class, BigDecimal.class, LocalDate.class, OffsetDateTime.class, UUID.class})
public interface DtoMapper {
<#list model.mapperMethods as m>

    /** ${j.doc(m.comment)} */
    @Named("${m.name}")
<#list m.mappings as a>
    ${a}
</#list>
    ${m.targetType} ${m.name}(${m.sourceType} source);
</#list>
}
