package org.sagebionetworks.bridge.play.controllers;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import play.mvc.Result;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.schedules.CompoundActivityDefinition;
import org.sagebionetworks.bridge.services.CompoundActivityDefinitionService;

/** Play controller for managing Compound Activity Definitions. */
@Controller
public class CompoundActivityDefinitionController extends BaseController {
    private CompoundActivityDefinitionService compoundActivityDefService;

    /** Service for Compound Activity Definitions, managed by Spring. */
    @Autowired
    public final void setCompoundActivityDefService(CompoundActivityDefinitionService compoundActivityDefService) {
        this.compoundActivityDefService = compoundActivityDefService;
    }

    /** Creates a compound activity definition. */
    public Result createCompoundActivityDefinition() throws JsonProcessingException {
        UserSession session = getAuthenticatedSession(Roles.DEVELOPER);

        CompoundActivityDefinition requestDef = parseJson(request(), CompoundActivityDefinition.class);
        CompoundActivityDefinition createdDef = compoundActivityDefService.createCompoundActivityDefinition(
                session.getStudyIdentifier(), requestDef);
        return created(CompoundActivityDefinition.PUBLIC_DEFINITION_WRITER.writeValueAsString(createdDef));
    }

    /** Deletes a compound activity definition. */
    public Result deleteCompoundActivityDefinition(String taskId) {
        UserSession session = getAuthenticatedSession(Roles.DEVELOPER);

        compoundActivityDefService.deleteCompoundActivityDefinition(session.getStudyIdentifier(), taskId);
        return okResult("Compound activity definition has been deleted.");
    }

    /** List all compound activity definitions in a study. */
    public Result getAllCompoundActivityDefinitionsInStudy() throws JsonProcessingException {
        UserSession session = getAuthenticatedSession(Roles.DEVELOPER);

        List<CompoundActivityDefinition> defList = compoundActivityDefService.getAllCompoundActivityDefinitionsInStudy(
                session.getStudyIdentifier());
        ResourceList<CompoundActivityDefinition> defResourceList = new ResourceList<>(defList);
        return ok(CompoundActivityDefinition.PUBLIC_DEFINITION_WRITER.writeValueAsString(defResourceList));
    }

    /** Get a compound activity definition by ID. */
    public Result getCompoundActivityDefinition(String taskId) throws JsonProcessingException {
        UserSession session = getAuthenticatedSession(Roles.DEVELOPER);

        CompoundActivityDefinition def = compoundActivityDefService.getCompoundActivityDefinition(
                session.getStudyIdentifier(), taskId);
        return ok(CompoundActivityDefinition.PUBLIC_DEFINITION_WRITER.writeValueAsString(def));
    }

    /** Update a compound activity definition. */
    public Result updateCompoundActivityDefinition(String taskId) throws JsonProcessingException {
        UserSession session = getAuthenticatedSession(Roles.DEVELOPER);

        CompoundActivityDefinition requestDef = parseJson(request(), CompoundActivityDefinition.class);
        CompoundActivityDefinition updatedDef = compoundActivityDefService.updateCompoundActivityDefinition(
                session.getStudyIdentifier(), taskId, requestDef);
        return ok(CompoundActivityDefinition.PUBLIC_DEFINITION_WRITER.writeValueAsString(updatedDef));
    }
}
