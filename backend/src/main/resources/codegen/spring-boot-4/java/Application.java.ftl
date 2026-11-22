package ${project.basePackage};

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ${project.appClass} {

    public static void main(String[] args) {
        SpringApplication.run(${project.appClass}.class, args);
    }
}
