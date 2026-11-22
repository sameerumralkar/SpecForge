package ${project.basePackage}.persistence;

<#if item.hasSource()>
import ${project.basePackage}.dto.${item.sourceType};
</#if>
import ${project.basePackage}.error.NotFoundException;
import ${project.basePackage}.error.PersistenceFailedException;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Loads and saves ${item.className} records<#if item.hasSource()>, converting to and from ${item.sourceType}</#if>. */
@Component
@Transactional
public class ${item.storeClass} {

    private final ${item.repositoryClass} repository;

    public ${item.storeClass}(${item.repositoryClass} repository) {
        this.repository = repository;
    }
<#if item.hasSource()>

    public ${item.sourceType} save(${item.sourceType} dto) {
        ${item.className} entity = new ${item.className}();
<#if item.idFromDto>
        entity.${item.id.setter()}(dto.${item.id.getter()}());
</#if>
<#if item.idStrategy == "UUID_IF_MISSING">
        if (entity.${item.id.getter()}() == null) {
            entity.${item.id.setter()}(UUID.randomUUID());
        }
</#if>
        apply(dto, entity);
        return toDto(saveEntity(entity));
    }

    @Transactional(readOnly = true)
    public ${item.sourceType} findById(${item.id.javaType} id) {
        return toDto(load(id));
    }

    public ${item.sourceType} update(${item.id.javaType} id, ${item.sourceType} dto) {
        ${item.className} entity = load(id);
        apply(dto, entity);
        return toDto(saveEntity(entity));
    }

    private ${item.sourceType} toDto(${item.className} entity) {
        ${item.sourceType} dto = new ${item.sourceType}();
<#if item.idFromDto>
        dto.${item.id.setter()}(entity.${item.id.getter()}());
</#if>
<#list item.columns as c>
        dto.${c.setter()}(entity.${c.getter()}());
</#list>
        return dto;
    }

    private void apply(${item.sourceType} dto, ${item.className} entity) {
<#list item.columns as c>
        entity.${c.setter()}(dto.${c.getter()}());
</#list>
    }
</#if>

    @Transactional(readOnly = true)
    public ${item.className} load(${item.id.javaType} id) {
        try {
            return repository.findById(id).orElseThrow(() -> new NotFoundException("${item.className} " + id + " was not found"));
        } catch (DataAccessException e) {
            throw new PersistenceFailedException("Could not load ${item.className} " + id, e);
        }
    }

    public ${item.className} saveEntity(${item.className} entity) {
        try {
            return repository.save(entity);
        } catch (DataAccessException e) {
            throw new PersistenceFailedException("Could not save ${item.className}", e);
        }
    }

    public void delete(${item.id.javaType} id) {
        try {
            repository.deleteById(id);
        } catch (DataAccessException e) {
            throw new PersistenceFailedException("Could not delete ${item.className} " + id, e);
        }
    }
}
