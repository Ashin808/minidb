package minidb.api;

import minidb.exec.Executor;
import minidb.table.Database;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/** Creates the engine objects once, when the app starts, and shares them with the controllers. */
@Configuration
public class DatabaseConfig {

    @Bean
    public Database database(@Value("${minidb.data-dir}") String dir) throws IOException {
        return new Database(dir);          // Spring calls close() on shutdown, so files are closed cleanly
    }

    @Bean
    public Executor executor(Database database) {
        return new Executor(database);
    }
}