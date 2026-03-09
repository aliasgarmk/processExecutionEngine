package com.unifize.processengine.api.controller;

import com.unifize.processengine.api.dto.request.RegisterUserRequest;
import com.unifize.processengine.engine.InMemoryUserResolver;
import com.unifize.processengine.model.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Users", description = "Register actors so their display names resolve correctly")
@RestController
@RequestMapping("/api/users")
public class UsersController {

    private final InMemoryUserResolver userResolver;

    public UsersController(InMemoryUserResolver userResolver) {
        this.userResolver = userResolver;
    }

    @Operation(
        summary = "Register a user",
        description = "Call this for every actor before starting instances. If skipped, the actor's displayName will fall back to their userId.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(examples = {
                @ExampleObject(name = "Carol (initiator)", value = """
                    { "userId": "carol", "displayName": "Carol Smith" }
                    """),
                @ExampleObject(name = "Alice (reviewer)", value = """
                    { "userId": "alice", "displayName": "Alice Nguyen" }
                    """)
            })
        )
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void registerUser(@Valid @RequestBody RegisterUserRequest request) {
        userResolver.register(new User(request.userId(), request.displayName()));
    }
}
