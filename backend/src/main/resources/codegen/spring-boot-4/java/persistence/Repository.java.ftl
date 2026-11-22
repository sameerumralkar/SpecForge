package ${project.basePackage}.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ${item.repositoryClass} extends JpaRepository<${item.className}, ${item.id.javaType}> {
}
