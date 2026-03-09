package com.unifize.processengine.api.controller;

import com.unifize.processengine.api.dto.request.LoadDefinitionRequest;
import com.unifize.processengine.api.dto.response.DefinitionResponse;
import com.unifize.processengine.engine.DefinitionRegistry;
import com.unifize.processengine.engine.ProcessEngine;
import com.unifize.processengine.exception.DefinitionValidationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Definitions", description = "Load and retrieve workflow blueprints")
@RestController
@RequestMapping("/api/definitions")
public class DefinitionsController {

    private final ProcessEngine processEngine;
    private final DefinitionRegistry definitionRegistry;

    public DefinitionsController(ProcessEngine processEngine, DefinitionRegistry definitionRegistry) {
        this.processEngine = processEngine;
        this.definitionRegistry = definitionRegistry;
    }

    @Operation(
        summary = "Load a process definition",
        description = "Validates and registers a workflow blueprint. Call this once before starting instances.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(examples = @ExampleObject(name = "CAPA Workflow", value = """
                {
                  "definitionId": "capa",
                  "name": "CAPA Workflow",
                  "publishedBy": "admin",
                  "steps": [
                    {
                      "stepId": "initiation",
                      "stepType": "TASK",
                      "assigneeRule": { "type": "INITIATOR" }
                    },
                    {
                      "stepId": "review",
                      "stepType": "APPROVAL",
                      "assigneeRule": { "type": "USER_IDS", "userIds": ["alice"] }
                    }
                  ],
                  "routingRules": [
                    {
                      "sourceStepId": "initiation",
                      "defaultRoute": true,
                      "targetStepIds": ["review"]
                    }
                  ]
                }
                """))
        )
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DefinitionResponse loadDefinition(@Valid @RequestBody LoadDefinitionRequest request)
            throws DefinitionValidationException {
        processEngine.loadDefinition(request.toDomain());
        return DefinitionResponse.from(definitionRegistry.resolveLatest(request.definitionId()));
    }

    @Operation(summary = "Get the latest version of a definition")
    @GetMapping("/{definitionId}")
    public DefinitionResponse getDefinition(
            @Parameter(description = "The definition ID used when it was loaded", example = "capa")
            @PathVariable String definitionId) {
        return DefinitionResponse.from(definitionRegistry.resolveLatest(definitionId));
    }
}
