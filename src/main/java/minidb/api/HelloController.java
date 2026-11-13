package minidb.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HelloController {

    /** The JSON shape of the reply. Spring turns a record into {"status":"...","message":"..."}. */
    public record Hello(String status, String message) {}

    @GetMapping("/hello")
    public Hello hello() {
        return new Hello("ok", "MiniDB API is running");
    }
}