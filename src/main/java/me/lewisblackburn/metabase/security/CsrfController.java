package me.lewisblackburn.metabase.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CsrfController {
    // The security filter supplies the XSRF-TOKEN cookie; no session is created here.
    @GetMapping("/auth/csrf")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void csrf() {}
}
