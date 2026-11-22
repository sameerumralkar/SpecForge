package ${project.basePackage}.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Stored form of <#if item.hasSource()>a ${item.sourceType}<#else>${item.className} (TODO: add its columns)</#if>. */
@jakarta.persistence.Entity
@jakarta.persistence.Table(name = "${item.tableName}")
public class ${item.className} {

    @jakarta.persistence.Id
<#if item.idStrategy == "IDENTITY">
    @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
<#elseif item.idStrategy == "GENERATED_UUID">
    @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
</#if>
    private ${item.id.javaType} ${item.id.name};
<#list item.columns as c>

    private ${c.javaType} ${c.name};
</#list>
<#list item.skipped as name>

    // TODO: "${name}" holds nested data and is not stored yet; add a relation or a JSON column.
</#list>

    public ${item.id.javaType} ${item.id.getter()}() {
        return ${item.id.name};
    }

    public void ${item.id.setter()}(${item.id.javaType} ${item.id.name}) {
        this.${item.id.name} = ${item.id.name};
    }
<#list item.columns as c>

    public ${c.javaType} ${c.getter()}() {
        return ${c.name};
    }

    public void ${c.setter()}(${c.javaType} ${c.name}) {
        this.${c.name} = ${c.name};
    }
</#list>
}
