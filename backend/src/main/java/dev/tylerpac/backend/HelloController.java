package dev.tylerpac.backend;

import java.security.Principal;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @CrossOrigin(origins = "http://localhost:5173")
    @GetMapping("/hello")
    public String hello(Principal principal) {
        if (principal == null) {
            return "Hello";
        }

        return "Hello, " + principal.getName() + "!";
    }
}
