package com.unifize.processengine.api.controller;

import com.unifize.processengine.api.dto.request.AdvanceStepRequest;
import com.unifize.processengine.api.dto.request.OpenStepRequest;
import com.unifize.processengine.api.dto.request.StartProcessRequest;
import com.unifize.processengine.api.dto.response.AuditEntryResponse;
import com.unifize.processengine.api.dto.response.InstanceResponse;
import com.unifize.processengine.api.dto.response.StepResultResponse;
import com.unifize.processengine.engine.ProcessEngine;
import com.unifize.processengine.engine.StateStore;
import com.unifize.processengine.exception.FieldValidationException;
import com.unifize.processengine.exception.InactiveInstanceException;
import com.unifize.processengine.exception.InvalidTransitionException;
import com.unifize.processengine.exception.OptimisticLockException;
import com.unifize.processengine.exception.UnauthorisedTransitionException;
import com.unifize.processengine.model.User;
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

import java.util.List;

@Tag(name = "Instances", description = "Start and drive individual workflow runs")
@RestController
@RequestMapping("/api/instances")
public class InstancesController {

    private final ProcessEngine processEngine;
    private final StateStore stateStore;

    public InstancesController(ProcessEngine processEngine, StateStore stateStore) {
        this.processEngine = processEngine;
        this.stateStore = stateStore;
    }

    @Operation(
        summary = "Start a new process instance",
        description = "Creates a run of a workflow definition. The first step starts PENDING. **Save the `instanceId`** from the response — you need it for every subsequent call.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(examples = @ExampleObject(name = "Start CAPA", value = """
                {
                  "definitionId": "capa",
                  "initiatorId": "carol",
                  "fields": { "severity": "High", "description": "Pump calibration off by 3%" }
                }
                """))
        )
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InstanceResponse startProcess(@Valid @RequestBody StartProcessRequest request)
            throws FieldValidationException {
        User initiator = new User(request.initiatorId(), request.initiatorId());
        return InstanceResponse.from(
                processEngine.startProcess(request.definitionId(), initiator, request.fields()));
    }

    @Operation(summary = "Get an instance by ID")
    @GetMapping("/{instanceId}")
    public InstanceResponse getInstance(
            @Parameter(description = "instanceId returned by POST /api/instances", example = "inst-abc123")
            @PathVariable String instanceId) {
        return InstanceResponse.from(stateStore.loadInstance(instanceId));
    }

    @Operation(
        summary = "Open a step  (PENDING → IN_PROGRESS)",
        description = "Must be called before `advance`. Starts the SLA clock for the step.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(examples = {
                @ExampleObject(name = "Carol opens initiation", value = """
                    { "actorId": "carol" }
                    """),
                @ExampleObject(name = "Alice opens review", value = """
                    { "actorId": "alice" }
                    """)
            })
        )
    )
    @PostMapping("/{instanceId}/steps/{stepId}/open")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void openStep(
            @Parameter(description = "instanceId returned by POST /api/instances", example = "inst-abc123")
            @PathVariable String instanceId,
            @Parameter(description = "stepId from the definition (e.g. initiation, review)", example = "initiation")
            @PathVariable String stepId,
            @Valid @RequestBody OpenStepRequest request)
            throws InactiveInstanceException, InvalidTransitionException,
            UnauthorisedTransitionException, OptimisticLockException {
        User actor = new User(request.actorId(), request.actorId());
        processEngine.openStep(instanceId, stepId, actor);
    }

    @Operation(
        summary = "Advance a step",
        description = """
            Applies an action to an IN_PROGRESS step. Call `open` first.

            | Step type | Valid actionType |
            |---|---|
            | TASK / CHECKLIST | SUBMIT |
            | APPROVAL | APPROVE, REJECT |
            | REVIEW | APPROVE, REJECT, SUBMIT |
            | PARALLEL_APPROVAL | APPROVE, REJECT (each participant) |

            `reason` is required for REJECT, REOPEN, REASSIGN.
            `fields` is required for SUBMIT.
            """,
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(examples = {
                @ExampleObject(name = "Submit (TASK step)", value = """
                    {
                      "actorId": "carol",
                      "actionType": "SUBMIT",
                      "fields": { "rootCause": "Worn seal on pump inlet" }
                    }
                    """),
                @ExampleObject(name = "Approve (APPROVAL step)", value = """
                    {
                      "actorId": "alice",
                      "actionType": "APPROVE"
                    }
                    """),
                @ExampleObject(name = "Reject with reason", value = """
                    {
                      "actorId": "alice",
                      "actionType": "REJECT",
                      "reason": "Root cause analysis incomplete"
                    }
                    """)
            })
        )
    )
    @PostMapping("/{instanceId}/steps/{stepId}/advance")
    public StepResultResponse advanceStep(
            @Parameter(description = "instanceId returned by POST /api/instances", example = "inst-abc123")
            @PathVariable String instanceId,
            @Parameter(description = "stepId from the definition (e.g. initiation, review)", example = "initiation")
            @PathVariable String stepId,
            @Valid @RequestBody AdvanceStepRequest request)
            throws InactiveInstanceException, InvalidTransitionException,
            UnauthorisedTransitionException, FieldValidationException, OptimisticLockException {
        User actor = new User(request.actorId(), request.actorId());
        return StepResultResponse.from(
                processEngine.advanceStep(instanceId, stepId, request.toAction(), actor));
    }

    @Operation(
        summary = "Get the audit trail for an instance",
        description = "Returns all state transitions ordered by sequence number ascending."
    )
    @GetMapping("/{instanceId}/audit")
    public List<AuditEntryResponse> getAuditTrail(
            @Parameter(description = "instanceId returned by POST /api/instances", example = "inst-abc123")
            @PathVariable String instanceId) {
        return processEngine.getAuditTrail(instanceId).stream()
                .map(AuditEntryResponse::from)
                .toList();
    }
}
