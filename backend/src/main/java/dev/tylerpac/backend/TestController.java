package dev.tylerpac.backend;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @CrossOrigin(origins = "http://localhost:5173")
    
    @GetMapping("/test")
    public String test() {
        return "Test";
    }
}
